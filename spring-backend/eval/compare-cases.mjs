#!/usr/bin/env node
/**
 * RAG on/off 对比脚本（run-cases.mjs 产物的专用对比器，零依赖）。
 *
 * 为什么另写而不复用 eval/compare.mjs：后者的入参契约是 eval.mjs 的
 * `cases[] + metrics`（16 例离线集），而 run-cases.mjs 产出的是 `results[]`，
 * 字段名与统计口径都不同；改建 compare.mjs 属破坏性改动，故独立成文件。
 *
 * 用法（在 spring-backend 目录）：
 *     node eval/compare-cases.mjs
 *     node eval/compare-cases.mjs --on=rag-on-32cases.json --off=rag-off-32cases.json
 *
 * 产出：控制台 markdown 表格 + eval/results/<onTag>-vs-<offTag>.md（供报告直接引用）。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const RESULTS_DIR = path.join(__dirname, 'results');

function arg(name, def) {
  const hit = process.argv.find((a) => a.startsWith(`--${name}=`));
  if (hit) return hit.split('=').slice(1).join('=');
  return def;
}

const resolve = (rel) => (path.isAbsolute(rel) ? rel : path.join(RESULTS_DIR, rel));
const onFile = resolve(arg('on', 'rag-on-32cases.json'));
const offFile = resolve(arg('off', 'rag-off-32cases.json'));
for (const f of [onFile, offFile]) {
  if (!fs.existsSync(f)) {
    console.error(`结果文件不存在: ${f}`);
    process.exit(2);
  }
}

const on = JSON.parse(fs.readFileSync(onFile, 'utf8'));
const off = JSON.parse(fs.readFileSync(offFile, 'utf8'));
const byId = (run) => new Map(run.results.map((r) => [r.id, r]));
const onMap = byId(on);
const offMap = byId(off);
const ids = [...new Set([...onMap.keys(), ...offMap.keys()])].sort();

const f = (x) => (x ? 'T' : '.');
const num = (x) => (typeof x === 'number' ? x : 0);
const lat = (r) => num(r.latency_ms);
const compiledOk = (r) => r && r.compile && r.compile.status === 'success';
const flags = (r) => {
  if (!r) return { gen: '.', code: '.', comp: '.', viol: '.', skip: false };
  if (r.skip) return { gen: 'skip', code: 'skip', comp: 'skip', viol: 'skip', skip: true };
  return {
    gen: f(!r.error),
    code: f(r.ok),
    comp: f(compiledOk(r)),
    viol: f(!!r.violation_free && r.ok),
    skip: false,
  };
};

const out = [];
const log = (s = '') => {
  out.push(s);
  console.log(s);
};

log(`# RAG on/off 对比（run-cases 32 用例集）`);
log();
log(`- on  : \`${path.basename(onFile)}\`  rag标签=${on.rag ?? '-'}  模型=${on.model}  运行时间=${on.runAt}`);
log(`- off : \`${path.basename(offFile)}\`  rag标签=${off.rag ?? '-'}  模型=${off.model}  运行时间=${off.runAt}`);
log();

// ---------- 逐用例 ----------
log(`## 1. 逐用例对比`);
log();
log('`T`=通过 `.`=未通过；gen=生成成功(无请求异常) code=有图表代码 comp=编译成功 violf=有代码且零违例；lat=chat 端到端耗时(ms)，Δ=on−off。');
log();
log('| 用例 | 名称 | on: gen code comp violf | off: gen code comp violf | lat(on) | lat(off) | Δ |');
log('|---|---|:-: :-: :-: :-:|:-: :-: :-: :-:|--:|--:|--:|');
for (const id of ids) {
  const a = onMap.get(id);
  const b = offMap.get(id);
  const fa = flags(a);
  const fb = flags(b);
  const name = (a && a.name) || (b && b.name) || '';
  const la = lat(a);
  const lb = lat(b);
  const d = la - lb;
  const cell = (x) => (x.skip ? 'skip skip skip skip' : `${x.gen} ${x.code} ${x.comp} ${x.viol}`);
  log(`| ${id} | ${name} | ${cell(fa)} | ${cell(fb)} | ${la || '-'} | ${lb || '-'} | ${la && lb ? (d >= 0 ? `+${d}` : d) : '-'} |`);
}
log();

// ---------- 总指标 ----------
const keys = ['total', 'executed', 'code_rate', 'compile_rate', 'first_pass_rate', 'zero_violation_rate', 'degraded_count', 'avg_latency_ms', 'p95_latency_ms'];
log(`## 2. 总指标对比`);
log();
log('| 指标 | on | off | Δ(on−off) |');
log('|---|--:|--:|--:|');
for (const k of keys) {
  const a = on.metrics[k];
  const b = off.metrics[k];
  const d = typeof a === 'number' && typeof b === 'number' ? +(a - b).toFixed(3) : '-';
  log(`| ${k} | ${a ?? '-'} | ${b ?? '-'} | ${d} |`);
}
log();

// ---------- 违例 ----------
const vc = (run) => run.metrics.violation_counts || {};
const ruleSet = [...new Set([...Object.keys(vc(on)), ...Object.keys(vc(off))])].sort();
log(`## 3. 静态违例分布`);
log();
if (!ruleSet.length) {
  log('两轮均无静态违例。');
} else {
  log('| 规则 | on | off |');
  log('|---|--:|--:|');
  for (const r of ruleSet) log(`| ${r} | ${vc(on)[r] || 0} | ${vc(off)[r] || 0} |`);
}
log();

// ---------- 编译失败 ----------
const failRows = [];
for (const id of ids) {
  for (const [side, m] of [['on', onMap], ['off', offMap]]) {
    const r = m.get(id);
    if (r && !r.skip && r.compile && r.compile.status !== 'success') {
      failRows.push(`| ${id} | ${r.name || ''} | ${side} | ${r.compile.status} | ${(r.compile.error || '').replace(/\n/g, ' ').slice(0, 200)} |`);
    }
  }
}
log(`## 4. 编译失败/超时明细`);
log();
if (!failRows.length) {
  log('两轮均无编译失败或超时。');
} else {
  log('| 用例 | 名称 | 轮次 | 状态 | 错误摘要 |');
  log('|---|---|:-:|---|---|');
  out.push(...failRows);
  failRows.forEach((r) => console.log(r));
}
log();

// ---------- 翻转 ----------
const flipped = ids.filter((id) => {
  const a = onMap.get(id);
  const b = offMap.get(id);
  if ((a && a.skip) || (b && b.skip)) return false;
  const pa = !!(a && a.ok && compiledOk(a));
  const pb = !!(b && b.ok && compiledOk(b));
  return pa !== pb;
});
log(`## 5. 通过与否翻转`);
log();
if (!flipped.length) log('无通过与否翻转的用例。');
else for (const id of flipped) {
  const a = onMap.get(id);
  const b = offMap.get(id);
  log(`- ${id} ${(a && a.name) || ''}：on=${a && a.ok && compiledOk(a) ? 'PASS' : 'FAIL'} / off=${b && b.ok && compiledOk(b) ? 'PASS' : 'FAIL'}`);
}
log();

// ---------- 降级 ----------
const deg = (m) => [...m.values()].filter((r) => !r.skip && r.model_used && r.model_used !== (m === onMap ? on.model : off.model));
const degOn = deg(onMap);
const degOff = deg(offMap);
log(`## 6. 模型通道降级`);
log();
if (!degOn.length && !degOff.length) {
  log(`两轮均无降级：31 例全部由请求通道 ${on.model} 完成。`);
} else {
  for (const [side, list] of [['on', degOn], ['off', degOff]]) {
    for (const r of list) log(`- ${side} ${r.id} ${r.name}：model_used=${r.model_used}`);
  }
}

const outFile = path.join(RESULTS_DIR, `${path.basename(onFile, '.json')}-vs-${path.basename(offFile, '.json')}.md`);
fs.writeFileSync(outFile, out.join('\n') + '\n', 'utf8');
console.error(`\nmarkdown -> ${outFile}`);
