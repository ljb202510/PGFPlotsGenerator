#!/usr/bin/env node
/**
 * 把某一轮 run-cases.mjs 产物（eval/results/<tag>.json）里编译成功的用例，
 * 逐个下载 PDF 并用 pdftoppm 转成 PNG，供「图表质量」逐张目视复核。
 *
 * 数据来源：GET /api/compile/{history_id}/pdf（CompileController 已提供的鉴权下载接口，
 * 无需查 userId）；只处理 compile.status === 'success' 的用例，失败/超时用例没有 PDF。
 *
 * 依赖：pdftoppm（TeXLive bin，已在 PATH；本项目环境无 ImageMagick）。
 *
 * 用法（在 spring-backend 目录，后端已启动）：
 *     node eval/render-charts.mjs --tag=rag-on-32cases --email=x@x.com --password=xxx
 *     node eval/render-charts.mjs --tag=rag-off-32cases --dpi=150
 *
 * 产出：eval/results/<tag>/pdf/hist<history_id>.pdf
 *       eval/results/<tag>/png/case<id>-hist<history_id>.png
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const RESULTS_DIR = path.join(__dirname, 'results');

function arg(name, def) {
  const hit = process.argv.find((a) => a.startsWith(`--${name}=`));
  if (hit) return hit.split('=').slice(1).join('=');
  return def;
}

const BASE = (process.env.VERIFY_BASE || arg('base', 'http://localhost:3000')).replace(/\/$/, '');
const TAG = arg('tag', '');
const EMAIL = process.env.EMAIL || arg('email', '');
const PASSWORD = process.env.PASSWORD || arg('password', '');
const DPI = arg('dpi', '150');
const ONLY = arg('only', '').split(',').filter(Boolean);

if (!TAG) {
  console.error('缺少 --tag（对应 eval/results/<tag>.json）');
  process.exit(2);
}
if (!EMAIL || !PASSWORD) {
  console.error('缺少账号。用法：node eval/render-charts.mjs --tag=xxx --email=x --password=y');
  process.exit(2);
}

const jsonPath = path.join(RESULTS_DIR, `${TAG}.json`);
if (!fs.existsSync(jsonPath)) {
  console.error(`结果文件不存在: ${jsonPath}`);
  process.exit(2);
}
const run = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));

const pdfDir = path.join(RESULTS_DIR, TAG, 'pdf');
const pngDir = path.join(RESULTS_DIR, TAG, 'png');
fs.mkdirSync(pdfDir, { recursive: true });
fs.mkdirSync(pngDir, { recursive: true });

async function login() {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
  });
  const json = await res.json().catch(() => null);
  const token = json && json.data && json.data.token;
  if (!token) throw new Error(`登录失败（${res.status}）`);
  return token;
}

async function downloadPdf(token, historyId, dest) {
  const res = await fetch(`${BASE}/api/compile/${historyId}/pdf`, {
    headers: { Authorization: `Bearer ${token}` },
    signal: AbortSignal.timeout(30000),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const buf = Buffer.from(await res.arrayBuffer());
  if (buf.length === 0) throw new Error('空响应');
  fs.writeFileSync(dest, buf);
  return buf.length;
}

function toPng(pdfFile, prefix) {
  // -singlefile：只输出第一页；pdftoppm 自动追加 .png
  execFileSync('pdftoppm', ['-png', '-r', DPI, '-singlefile', pdfFile, prefix], { stdio: 'pipe' });
  return `${prefix}.png`;
}

async function main() {
  const token = await login();
  console.error(`[INFO] tag=${TAG} base=${BASE} dpi=${DPI} cases=${run.results.length}`);

  const targets = run.results.filter((r) => r.history_id && r.compile?.status === 'success' && (!ONLY.length || ONLY.includes(r.id)));
  let okPdf = 0;
  let okPng = 0;
  const failures = [];

  for (const r of targets) {
    const pdfFile = path.join(pdfDir, `hist${r.history_id}.pdf`);
    const prefix = path.join(pngDir, `case${r.id}-hist${r.history_id}`);
    try {
      if (!fs.existsSync(pdfFile)) await downloadPdf(token, r.history_id, pdfFile);
      okPdf += 1;
      toPng(pdfFile, prefix);
      okPng += 1;
      console.error(`[OK ] case ${r.id} hist${r.history_id} -> ${path.basename(prefix)}.png`);
    } catch (e) {
      failures.push({ id: r.id, history_id: r.history_id, error: e.message });
      console.error(`[ERR] case ${r.id} hist${r.history_id}: ${e.message}`);
    }
  }

  console.error(`\nSUMMARY: compiled=${targets.length} pdf=${okPdf} png=${okPng} failed=${failures.length}`);
  console.error(`pdf dir -> ${pdfDir}`);
  console.error(`png dir -> ${pngDir}`);
  if (failures.length) console.error(`failures -> ${JSON.stringify(failures)}`);
}

main().catch((e) => {
  console.error(`[ERROR] ${e.message}`);
  process.exit(1);
});
