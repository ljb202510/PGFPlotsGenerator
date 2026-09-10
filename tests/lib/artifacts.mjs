// 产物落盘：截图、代码、PDF、日志、results.json。统一命名便于回溯。
import fs from 'node:fs';
import path from 'node:path';
import { RESULTS_DIR, ROOT } from './config.mjs';

export function ensureResultsDir() {
  fs.mkdirSync(RESULTS_DIR, { recursive: true });
  return RESULTS_DIR;
}

export function resultsDir() {
  return RESULTS_DIR;
}

/** hello/ 相对路径（报告引用用，避免绝对路径泄露） */
export function relFromRoot(p) {
  if (!p) return '';
  const rel = path.relative(ROOT, p);
  return rel.split(path.sep).join('/');
}

export function saveJson(fileName, obj) {
  ensureResultsDir();
  const p = path.join(RESULTS_DIR, fileName);
  fs.writeFileSync(p, JSON.stringify(obj, null, 2), 'utf8');
  return p;
}

export function saveText(fileName, text) {
  ensureResultsDir();
  const p = path.join(RESULTS_DIR, fileName);
  fs.writeFileSync(p, text ?? '', 'utf8');
  return p;
}

export function saveBuffer(fileName, buf) {
  ensureResultsDir();
  const p = path.join(RESULTS_DIR, fileName);
  fs.writeFileSync(p, Buffer.from(buf));
  return p;
}

/** 保存某用例生成的图表代码 */
export function saveChartCode(caseId, code) {
  return saveText(`case${caseId}-chart_code.tex`, code || '');
}

/** 保存编译出的 PDF */
export function savePdf(caseId, historyId, buf) {
  return saveBuffer(`case${caseId}-hist${historyId}.pdf`, buf);
}

/** 截图（失败时也要能截到，故内部吞掉异常） */
export async function shot(page, fileName) {
  try {
    ensureResultsDir();
    const p = path.join(RESULTS_DIR, fileName);
    await page.screenshot({ path: p });
    return p;
  } catch {
    return null;
  }
}

export function writeResults(results) {
  return saveJson('results.json', results);
}
