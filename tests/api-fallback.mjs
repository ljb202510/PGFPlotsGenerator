// 兜底通道：绕过前端，用 Node 原生 fetch 直连后端 REST。
// 用途：UI 通道失败时复现同一用例，区分「前端交互问题」与「生成/编译问题」。
// 也可独立运行：node tests/api-fallback.mjs --case 01 --model deepseek
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { API_BASE, ROOT, TIMEOUTS, getCredentials, parseArgs } from './lib/config.mjs';
import { CASES, pickCases } from './lib/cases.mjs';

const __filename = fileURLToPath(import.meta.url);

async function httpJson(url, { method = 'GET', token, body, timeout = TIMEOUTS.HTTP } = {}) {
  const isForm = typeof FormData !== 'undefined' && body instanceof FormData;
  const resp = await fetch(url, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body && !isForm ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? (isForm ? body : JSON.stringify(body)) : undefined,
    signal: AbortSignal.timeout(timeout),
  });
  const text = await resp.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    /* 非 JSON 响应，保留 text */
  }
  return { status: resp.status, ok: resp.ok, json, text };
}

export async function loginApi(creds = getCredentials()) {
  const { status, json, text } = await httpJson(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    body: { email: creds.email, password: creds.password },
  });
  if (status !== 200 || !json?.token) {
    throw new Error(`登录失败（HTTP ${status}）：${json?.message || text || '未知错误'}`);
  }
  return { token: json.token, user: json.user };
}

export async function uploadDataset(token, absFilePath) {
  const buf = fs.readFileSync(absFilePath);
  const fileName = path.basename(absFilePath);
  const name = fileName.replace(/\.[^/.]+$/, '');
  const fd = new FormData();
  fd.append('name', name);
  fd.append('description', `自动化测试上传 ${new Date().toLocaleString()}`);
  fd.append('file', new Blob([buf]), fileName);
  const { status, json, text } = await httpJson(`${API_BASE}/api/datasets`, {
    method: 'POST',
    token,
    body: fd,
    timeout: TIMEOUTS.UPLOAD,
  });
  if (json?.code !== 200 || !json?.data?.data_id) {
    throw new Error(`上传失败（HTTP ${status}）：${json?.message || text || '未知错误'}`);
  }
  return json.data;
}

export async function chatOnce(token, { message, dataIds, model = 'qwen' }) {
  const { status, json, text } = await httpJson(`${API_BASE}/api/chat`, {
    method: 'POST',
    token,
    body: {
      message,
      data_ids: dataIds && dataIds.length ? dataIds : undefined,
      model,
    },
    timeout: TIMEOUTS.GENERATE,
  });
  if (!json?.success) {
    return { ok: false, status, error: json?.message || text || `HTTP ${status}` };
  }
  return { ok: true, status, data: json.data };
}

export async function compileOnce(token, historyId) {
  const { status, json, text } = await httpJson(`${API_BASE}/api/compile/${historyId}`, {
    method: 'POST',
    token,
    body: {},
    timeout: TIMEOUTS.COMPILE,
  });
  if (!json?.success) {
    return { ok: false, status, error: json?.message || text || `HTTP ${status}` };
  }
  return { ok: true, status, data: json.data };
}

export async function fetchPdfBuffer(token, historyId) {
  const resp = await fetch(`${API_BASE}/api/compile/${historyId}/pdf`, {
    headers: { Authorization: `Bearer ${token}` },
    signal: AbortSignal.timeout(TIMEOUTS.HTTP),
  });
  if (!resp.ok) throw new Error(`获取 PDF 失败：HTTP ${resp.status}`);
  return Buffer.from(await resp.arrayBuffer());
}

/**
 * 直连跑单例：上传（如需）→ chat → compile → 取 PDF。
 * @returns {{ok:boolean, stage:string, model:string, historyId:(number|null), chartCode:string, pdfBuffer:(Buffer|null), error:string}}
 */
export async function runCaseViaApi(caseDef, { model = 'deepseek', token } = {}) {
  const result = {
    id: caseDef.id,
    ok: false,
    stage: '-',
    model,
    historyId: null,
    chartCode: '',
    pdfBuffer: null,
    error: '',
  };
  try {
    const tk = token || (await loginApi()).token;
    let dataIds;
    if (caseDef.upload && caseDef.file) {
      result.stage = '上传';
      const ds = await uploadDataset(tk, path.join(ROOT, caseDef.file));
      dataIds = [ds.data_id];
    }
    result.stage = '生成';
    const chat = await chatOnce(tk, { message: caseDef.prompt, dataIds, model });
    if (!chat.ok) {
      result.error = chat.error;
      return result;
    }
    result.historyId = chat.data.history_id;
    result.chartCode = chat.data.chart_code || '';
    result.stage = '编译';
    const comp = await compileOnce(tk, result.historyId);
    if (!comp.ok) {
      result.error = comp.error;
      return result;
    }
    result.stage = '渲染';
    result.pdfBuffer = await fetchPdfBuffer(tk, result.historyId);
    result.ok = true;
    result.stage = '-';
    return result;
  } catch (e) {
    result.error = e.message;
    return result;
  }
}

// ---- 独立 CLI：node tests/api-fallback.mjs --cases 01,02 --model deepseek ----
const isMain =
  process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === pathToFileURL(__filename).href;

if (isMain) {
  const args = parseArgs();
  const cases = pickCases(args.cases);
  const model = args.model === 'qwen' ? 'qwen' : 'deepseek';
  const { ensureResultsDir, saveChartCode, savePdf, writeResults } = await import('./lib/artifacts.mjs');
  const { hasPdftoppm, pdfToPng } = await import('./lib/pdf-to-png.mjs');
  ensureResultsDir();
  const { token } = await loginApi();
  const out = [];
  for (const c of cases) {
    console.log(`[API] 用例 ${c.id} (${c.chart}) - model=${model} ...`);
    const r = await runCaseViaApi(c, { model, token });
    const ev = {};
    if (r.chartCode) ev.chartCode = saveChartCode(c.id, r.chartCode);
    if (r.pdfBuffer && r.historyId) {
      ev.pdf = savePdf(c.id, r.historyId, r.pdfBuffer);
      if (hasPdftoppm()) {
        try {
          ev.png = pdfToPng(ev.pdf, ev.pdf.replace(/\.pdf$/i, '.png'));
        } catch (e) {
          console.log(`  PDF→PNG 失败：${e.message}`);
        }
      }
    }
    console.log(r.ok ? `  ✓ 通过` : `  ✗ 失败 @ ${r.stage}：${r.error}`);
    out.push({ ...r, pdfBuffer: undefined, evidence: ev });
  }
  writeResults({ channel: 'api-only', model, results: out });
  console.log('\n结果已写入 tests/results/。');
}
