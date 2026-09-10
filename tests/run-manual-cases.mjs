// 一键入口：串行跑 manual-test-cases.md 的 01–10。
//   UI 主通道（Playwright）→ 失败自动切 Deepseek 重试一次 → 仍失败走 API 直连复现归属。
// 用法见 tests/README.md。凭据通过环境变量 TEST_EMAIL / TEST_PASSWORD 注入。
import fs from 'node:fs';
import { API_BASE, WEB_BASE, REPORT_PATH, RESULTS_DIR, getCredentials, parseArgs } from './lib/config.mjs';
import { pickCases } from './lib/cases.mjs';
import {
  ensureResultsDir,
  writeResults,
  saveChartCode,
  savePdf,
  relFromRoot,
  resultsDir,
} from './lib/artifacts.mjs';
import { hasPdftoppm, pdfToPng } from './lib/pdf-to-png.mjs';
import { buildReport, formatDuration } from './lib/report.mjs';
import { runCaseViaApi, loginApi } from './api-fallback.mjs';
import { createSession, runCaseInUi, closeSession } from './ui-runner.mjs';

const args = parseArgs();
const BASE_MODEL = args.model === 'deepseek' ? 'deepseek' : 'qwen';
const RETRY_MODEL = 'deepseek';
const HEADLESS = !args.headed;
const API_ONLY = Boolean(args['api-only']);
const NO_RETRY = Boolean(args['no-retry']);
const DRY = Boolean(args['dry-run']);
const CASES = pickCases(args.cases);

const truncate = (s, n = 400) => String(s || '').replace(/\s+/g, ' ').slice(0, n);

function printDryRun() {
  console.log('=== 干跑（不联网、不登录、不调用 AI）===');
  console.log(`后端 API   : ${API_BASE}`);
  console.log(`前端 WEB   : ${WEB_BASE}`);
  console.log(`用例       : ${CASES.map((c) => c.id).join(', ')}（共 ${CASES.length} 例）`);
  console.log(`默认模型   : ${BASE_MODEL}${NO_RETRY ? '' : `（失败自动切 ${RETRY_MODEL} 重试一次）`}`);
  console.log(`通道       : ${API_ONLY ? '仅 API' : 'UI 主通道 + API 兜底'}`);
  console.log(
    `凭据环境变量: TEST_EMAIL=${process.env.TEST_EMAIL ? '已设置' : '未设置'}，TEST_PASSWORD=${
      process.env.TEST_PASSWORD ? '已设置' : '未设置'
    }`
  );
  console.log(`PDF→PNG    : ${hasPdftoppm() ? 'pdftoppm 可用' : '缺少 pdftoppm/magick（将跳过 PNG 渲染）'}`);
  console.log(`结果目录   : ${relFromRoot(resultsDir())}`);
  console.log(`报告路径   : ${relFromRoot(REPORT_PATH)}`);
  console.log('\n用例清单：');
  for (const c of CASES) {
    console.log(`  ${c.id}  ${c.chart.padEnd(16, ' ')} 上传=${c.upload ? c.file : '否'}`);
  }
}

async function reachable(url) {
  try {
    const resp = await fetch(url, { signal: AbortSignal.timeout(5000) });
    return { ok: true, status: resp.status };
  } catch (e) {
    return { ok: false, error: e.message };
  }
}

function summarize(c, r, apiMode) {
  const uploaded = c.upload ? (r.uploaded !== undefined ? r.uploaded : r.stage !== '上传') : true;
  if (apiMode) {
    return {
      uploaded,
      aiGenerated: Boolean(r.historyId),
      codeGenerated: Boolean(r.chartCode && r.chartCode.includes('\\begin{tikzpicture}')),
      pdfCompiled: r.ok || r.stage === '渲染',
      pdfRendered: Boolean(r.ok),
    };
  }
  return {
    uploaded,
    aiGenerated: Boolean(r.aiGenerated),
    codeGenerated: Boolean(r.codeGenerated),
    pdfCompiled: Boolean(r.pdfCompiled),
    pdfRendered: Boolean(r.ok),
  };
}

function toRecord(c, r, apiMode) {
  const rec = {
    id: c.id,
    ...summarize(c, r, apiMode),
    result: r.ok ? 'pass' : 'fail',
    stage: r.ok ? '-' : r.stage || '未知',
    model: r.model || BASE_MODEL,
    retried: Boolean(r.__retried),
    checks: c.checks,
    evidence: {},
    note: r.note || '',
    errorText: r.ok ? '' : truncate(r.error, 800),
  };
  if (!apiMode && r.ok && r.dialogVisible === false) {
    rec.result = 'suspect';
    rec.note = [rec.note, 'PDF 已生成但未捕获到预览对话框（请核对截图）'].filter(Boolean).join('；');
  }
  return rec;
}

async function collectEvidence(c, r) {
  const ev = {};
  if (r.chartCode) ev.chartCode = relFromRoot(saveChartCode(c.id, r.chartCode));
  if (r.screenshot) ev.screenshot = relFromRoot(r.screenshot);
  if (r.dialogScreenshot) ev.dialogScreenshot = relFromRoot(r.dialogScreenshot);
  if (r.pdfBuffer && r.historyId) {
    const pdfPath = savePdf(c.id, r.historyId, r.pdfBuffer);
    ev.pdf = relFromRoot(pdfPath);
    if (hasPdftoppm()) {
      try {
        ev.png = relFromRoot(pdfToPng(pdfPath, pdfPath.replace(/\.pdf$/i, '.png')));
      } catch (e) {
        console.log(`  PDF→PNG 渲染失败：${e.message}`);
      }
    }
  }
  return ev;
}

async function main() {
  if (DRY) {
    printDryRun();
    return;
  }

  let creds;
  try {
    creds = getCredentials();
  } catch (e) {
    console.error(`✗ ${e.message}`);
    process.exitCode = 2;
    return;
  }

  ensureResultsDir();

  // 前置自检
  const apiUp = await reachable(`${API_BASE}/api/auth/validate`);
  if (!apiUp.ok) {
    console.error(`✗ 后端不可达（${API_BASE}）：${apiUp.error}\n  请先启动后端：cd backend && npm run dev`);
    process.exitCode = 3;
    return;
  }
  if (!API_ONLY) {
    const webUp = await reachable(WEB_BASE);
    if (!webUp.ok) {
      console.error(`✗ 前端不可达（${WEB_BASE}）：${webUp.error}\n  请先启动前端：npm run serve`);
      process.exitCode = 3;
      return;
    }
  }
  if (!hasPdftoppm()) {
    console.log('⚠ 未检测到 pdftoppm/magick，将跳过 PDF→PNG 渲染（不影响编译判定）。');
  }

  const startedAt = new Date();
  console.log(`▶ 开始：${CASES.length} 例，通道=${API_ONLY ? 'API' : 'UI'}，默认模型=${BASE_MODEL}`);

  let session = null;
  let token = '';
  try {
    if (API_ONLY) {
      token = (await loginApi(creds)).token;
    } else {
      session = await createSession({ headless: HEADLESS, channel: args.channel, creds });
      token = session.token;
    }
  } catch (e) {
    console.error(`✗ 登录失败：${e.message}`);
    process.exitCode = 4;
    await closeSession(session);
    return;
  }
  console.log('✓ 登录成功');

  const results = [];
  for (const c of CASES) {
    console.log(`\n===== 用例 ${c.id}：${c.chart} =====`);
    let r;
    try {
      if (API_ONLY) {
        r = await runCaseViaApi(c, { model: BASE_MODEL, token });
      } else {
        r = await runCaseInUi(session.page, token, c, { model: BASE_MODEL });
        if (!r.ok && !NO_RETRY) {
          console.log(`  ⚠ 失败：${truncate(r.error, 200)}\n  ↻ 切换 ${RETRY_MODEL} 重试一次…`);
          const retry = await runCaseInUi(session.page, token, c, { model: RETRY_MODEL });
          retry.__retried = true;
          if (retry.ok) {
            r = retry;
          } else {
            console.log(`  ⚠ 重试仍失败：${truncate(retry.error, 200)}`);
            console.log('  ↻ 启用 API 兜底复现…');
            const a = await runCaseViaApi(c, { model: RETRY_MODEL, token });
            r.note = `UI 两次失败：${truncate(retry.error, 200)}；API 兜底：${
              a.ok ? '成功（疑似前端交互问题）' : '同样失败（' + truncate(a.error, 200) + '）'
            }`;
            r = { ...retry, note: r.note };
          }
        }
      }
    } catch (e) {
      r = { ok: false, stage: '异常', error: e.message, chartCode: '', pdfBuffer: null, historyId: null };
    }

    const rec = toRecord(c, r, API_ONLY);
    rec.evidence = await collectEvidence(c, r);
    results.push(rec);
    const icon = rec.result === 'pass' ? '✓' : rec.result === 'suspect' ? '?' : '✗';
    console.log(`  ${icon} ${rec.result}${rec.retried ? '（重试）' : ''}${rec.note ? ' — ' + rec.note : ''}`);
  }

  await closeSession(session);

  const finishedAt = new Date();
  const meta = {
    date: startedAt.toISOString().slice(0, 10),
    apiBase: API_BASE,
    webBase: WEB_BASE,
    uiChannel: API_ONLY ? '未使用（仅 API）' : `Playwright Chromium${HEADLESS ? '（headless）' : '（有头）'}`,
    apiFallback: API_ONLY ? '主通道' : '失败时启用',
    defaultModel: BASE_MODEL,
    retryModel: NO_RETRY ? '' : RETRY_MODEL,
    scope: CASES.map((c) => c.id).join(','),
    startedAt: startedAt.toLocaleString(),
    finishedAt: finishedAt.toLocaleString(),
    durationText: formatDuration(finishedAt - startedAt),
  };

  const payload = { meta, results, evidenceDir: relFromRoot(RESULTS_DIR) };
  writeResults(payload);
  fs.writeFileSync(REPORT_PATH, buildReport(meta, results), 'utf8');

  const counts = results.reduce((a, r) => ((a[r.result] = (a[r.result] || 0) + 1), a), {});
  console.log(`\n===== 完成 =====`);
  console.log(
    `通过 ${counts.pass || 0} / 存疑 ${counts.suspect || 0} / 失败 ${counts.fail || 0}（共 ${results.length} 例，用时 ${meta.durationText}）`
  );
  console.log(`结果：${relFromRoot(RESULTS_DIR)}/results.json`);
  console.log(`报告：${relFromRoot(REPORT_PATH)}`);
}

main().catch(async (e) => {
  console.error(`✗ 运行异常：${e?.stack || e}`);
  process.exitCode = 1;
});
