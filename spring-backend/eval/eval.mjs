/**
 * PGFPlotsGenerator 后端 - E1 离线评估集脚本（Node 版，零第三方依赖）
 *
 * 用法（在 spring-backend 目录，后端已启动）：
 *     node eval/eval.mjs --tag=rag-off
 *     node eval/eval.mjs --tag=rag-on  --base=http://localhost:3000
 *
 * 逐用例流程：
 *   1) POST /api/chat（{message, model:"deepseek"}，超时 120s）→ 记 history_id 与耗时；
 *   2) 若 chat success 且 chart_code 非空 → POST /api/compile/{history_id}（超时 60s）→ compiled=true；
 *   3) 首轮通过率 = 通过数 / 总数，其中「通过」= chat success && chart_code 非空 && compile success。
 *
 * 产出：控制台汇总表 + eval/results/<tag>.json。
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.env.VERIFY_BASE || process.argv.find((a) => a.startsWith('--base='))?.slice('--base='.length) || 'http://localhost:3000';
const tagArg = process.argv.find((a) => a.startsWith('--tag='));
const TAG = tagArg ? tagArg.slice('--tag='.length) : 'run';

const CASES = JSON.parse(fs.readFileSync(path.join(ROOT, 'cases.json'), 'utf8'));
const CHAT_TIMEOUT = 120000;
const COMPILE_TIMEOUT = 60000;

async function req(method, url, { token, body, timeout = 15000 } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const started = Date.now();
  let json = null;
  let status = 0;
  try {
    const res = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(timeout),
    });
    status = res.status;
    try {
      json = await res.json();
    } catch (e) {
      // 非 JSON 响应
    }
  } catch (e) {
    // 超时/网络失败：json 保持 null，status 保持 0
  }
  return { status, json, latency_ms: Date.now() - started };
}

function p95(values) {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.ceil(sorted.length * 0.95) - 1];
}

/** 复用 verify.js 的登录：管理员 admin123 / 666666 → data.token */
async function login() {
  const r = await req('POST', `${BASE}/api/auth/admin/login`, {
    body: { adminAccount: 'admin123', password: '666666' },
  });
  const token = r.json && r.json.data && r.json.data.token;
  if (!token) {
    throw new Error(`管理员登录失败（${r.status}）: ${r.json ? r.json.message : '无响应'}`);
  }
  return token;
}

async function main() {
  console.log(`[INFO] tag=${TAG}  base=${BASE}  cases=${CASES.length}`);
  const token = await login();
  console.log('[INFO] 登录成功\n');

  const cases = [];
  let genOk = 0;
  let compileOkCount = 0;
  let pass = 0;

  for (const c of CASES) {
    const chat = await req('POST', `${BASE}/api/chat`, {
      token,
      body: { message: c.message, model: 'deepseek' },
      timeout: CHAT_TIMEOUT,
    });
    const success = Boolean(chat.json && chat.json.success);
    const chartCode = chat.json && chat.json.data && chat.json.data.chart_code;
    const historyId = chat.json && chat.json.data && chat.json.data.history_id;
    const hasCode = Boolean(chartCode && String(chartCode).trim().length > 0);

    let compiled = false;
    if (success && hasCode && historyId) {
      const compile = await req('POST', `${BASE}/api/compile/${historyId}`, {
        token,
        timeout: COMPILE_TIMEOUT,
      });
      compiled = Boolean(compile.json && compile.json.success && compile.json.data.pdf_path);
    }

    if (success) genOk += 1;
    if (compiled) compileOkCount += 1;
    if (success && hasCode && compiled) pass += 1;

    const mark = success && hasCode && compiled ? 'PASS' : 'FAIL';
    console.log(`  [${mark}] ${c.id.padEnd(14)} gen_success=${success} code=${hasCode} compiled=${compiled} chat=${chat.latency_ms}ms (history_id=${historyId ?? '-'})`);

    cases.push({
      id: c.id,
      ok: success && hasCode && compiled,
      gen_ok: success,
      code_ok: hasCode,
      compile_ok: compiled,
      latency_ms: chat.latency_ms,
      history_id: historyId ?? null,
    });
  }

  const total = cases.length;
  const genSuccessRate = total ? genOk / total : 0;
  const compileRate = genOk ? compileOkCount / genOk : 0;
  const firstPassRate = total ? pass / total : 0;
  const chatLats = cases.map((x) => x.latency_ms).filter((x) => x >= 0);
  const avgLatency = chatLats.length ? chatLats.reduce((a, b) => a + b, 0) / chatLats.length : 0;

  const metrics = {
    total,
    gen_success_rate: +genSuccessRate.toFixed(3),
    compile_rate: +compileRate.toFixed(3),
    first_pass_rate: +firstPassRate.toFixed(3),
    avg_latency_ms: Math.round(avgLatency),
    p95_latency_ms: Math.round(p95(chatLats)),
  };

  const result = {
    tag: TAG,
    run_at: new Date().toLocaleString('sv-SE', { timeZone: 'Asia/Shanghai' }) + '+08:00',
    base: BASE,
    cases,
    metrics,
  };

  const resultsDir = path.join(ROOT, 'results');
  fs.mkdirSync(resultsDir, { recursive: true });
  const outFile = path.join(resultsDir, `${TAG}.json`);
  fs.writeFileSync(outFile, JSON.stringify(result, null, 2), 'utf8');

  console.log('\n================ 汇总 ================');
  console.log(`  总数             : ${metrics.total}`);
  console.log(`  生成成功率       : ${metrics.gen_success_rate}`);
  console.log(`  可编译率(占生成) : ${metrics.compile_rate}`);
  console.log(`  首轮通过率       : ${metrics.first_pass_rate}`);
  console.log(`  平均耗时(chat)   : ${metrics.avg_latency_ms}ms`);
  console.log(`  P95 耗时(chat)   : ${metrics.p95_latency_ms}ms`);
  console.log(`\n  已写入: ${outFile}`);
}

main().catch((e) => {
  console.error(`[ERROR] ${e.message}`);
  process.exit(1);
});