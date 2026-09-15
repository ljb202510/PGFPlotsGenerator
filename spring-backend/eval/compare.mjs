#!/usr/bin/env node
/**
 * RAG on/off 评估结果对比脚本（零依赖）。
 *
 * 读两份 eval/results/<tag>.json（由 eval.mjs 产出），按用例 id 对齐，
 * 输出：逐用例 conv (ok / code / compile / violation_free / latency) 的 on vs off，
 * 以及总指标（生成率/可编译率/首轮通过率/零违例率/平均耗时/P95）的 delta。
 *
 * 用法（在 spring-backend 目录）：
 *     node eval/compare.mjs --on=eval/results/v3.0-qwen-rag-on.json --off=eval/results/rag-off.json
 *  若 --on/--off 缺省分别取默认 on=rag-on.json、off=rag-off.json 的产物。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const resultsDir = path.join(__dirname, 'results');

function arg(name, def) {
  const hit = process.argv.find((a) => a.startsWith(`--${name}=`));
  if (hit) return hit.split('=').slice(1).join('=');
  return def;
}
const load = (rel) => JSON.parse(fs.readFileSync(path.isAbsolute(rel) ? rel : path.join(resultsDir, rel), 'utf8'));

const onArg = arg('on', 'rag-on.json');
const offArg = arg('off', 'rag-off.json');

const on = load(onArg);
const off = load(offArg);
const byId = (r) => new Map(r.cases.map((c) => [c.id, c]));

const onMap = byId(on);
const offMap = byId(off);
const ids = [...new Set([...onMap.keys(), ...offMap.keys()])].sort();

const pct = (n, d) => (d ? (n / d).toFixed(3) : '-');

console.log('==== 逐用例对比 (on=开RAG / off=关RAG) ====');
console.log('id'.padEnd(20) + 'gen  code comp violf | gen  code comp violf | lat(on)  lat(off)  Δ');
console.log('---------------------- ' + '-'.repeat(9) + ' -- ' + '-'.repeat(9) + '  ---------  ---------  --');
const rows = [];
for (const id of ids) {
  const a = onMap.get(id) || { ok: false, gen_ok: false, code_ok: false, compile_ok: false, violation_free: false, latency_ms: 0 };
  const b = offMap.get(id) || { ok: false, gen_ok: false, code_ok: false, compile_ok: false, violation_free: false, latency_ms: 0 };
  const f = (x) => (x ? 'T' : '.');
  const latA = a.latency_ms ?? 0;
  const latB = b.latency_ms ?? 0;
  console.log(
    id.padEnd(20) +
    `${f(a.gen_ok)}    ${f(a.code_ok)}  ${f(a.compile_ok)}  ${f(a.violation_free)}  | ` +
    `${f(b.gen_ok)}    ${f(b.code_ok)}  ${f(b.compile_ok)}  ${f(b.violation_free)}  | ` +
    `${String(latA).padStart(6)}   ${String(latB).padStart(6)}   ${(latA - latB) >= 0 ? '+' : ''}${latA - latB}`
  );
  rows.push({ id, on: a, off: b });
}

console.log('\n==== 总指标对比 ====');
const m = (k) => (obj) => obj.metrics ? obj.metrics[k] : 0;
const M = (k) => (on.metrics?.[k] !== undefined) || (off.metrics?.[k] !== undefined);
const metKeys = ['total', 'gen_success_rate', 'compile_rate', 'first_pass_rate', 'zero_violation_rate', 'avg_latency_ms', 'p95_latency_ms'];
for (const k of metKeys) {
  const a = on.metrics?.[k];
  const b = off.metrics?.[k];
  const line = `${k.padEnd(20)} on=${String(a).padStart(7)}  off=${String(b).padStart(7)}  Δ=${String(+((a ?? 0) - (b ?? 0)).toFixed(3)).padStart(7)}`;
  console.log(line);
}

// 额外：case 级 ok 翻转明细
const flipped = rows.filter((r) => r.on.ok !== r.off.ok);
if (flipped.length) {
  console.log('\n==== 通过与否翻转的用例 ====');
  for (const r of flipped) {
    console.log(`  ${r.id.padEnd(20)} on=${r.on.ok ? 'PASS' : 'FAIL'}  off=${r.off.ok ? 'PASS' : 'FAIL'}`);
  }
} else {
  console.log('\n（无通过与否翻转的用例）');
}