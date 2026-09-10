// PDF 渲染为 PNG：优先 pdftoppm（Poppler），退化 ImageMagick(magick)。用于离线图像核对。
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

function which(cmd) {
  try {
    execFileSync(cmd, ['-v'], { stdio: 'ignore' });
    return true;
  } catch {
    // pdftoppm -v 会写 stderr 且可能非 0；用 where/which 再确认一次
    try {
      execFileSync(process.platform === 'win32' ? 'where' : 'which', [cmd], { stdio: 'ignore' });
      return true;
    } catch {
      return false;
    }
  }
}

export function hasPdftoppm() {
  return which('pdftoppm');
}

export function hasMagick() {
  return which('magick');
}

/**
 * 将 PDF 首页渲染为 PNG。
 * @param {string} pdfPath 输入 PDF
 * @param {string} outPngPath 输出 PNG（须以 .png 结尾）
 * @param {{dpi?: number}} [opts]
 * @returns {string} 实际生成的 PNG 路径
 */
export function pdfToPng(pdfPath, outPngPath, opts = {}) {
  const dpi = String(opts.dpi || 150);
  const base = outPngPath.replace(/\.png$/i, '');
  if (!fs.existsSync(pdfPath)) throw new Error(`PDF 不存在：${pdfPath}`);

  if (hasPdftoppm()) {
    execFileSync('pdftoppm', ['-png', '-r', dpi, '-singlefile', '-f', '1', '-l', '1', pdfPath, base], {
      stdio: 'ignore',
    });
    const out = `${base}.png`;
    if (fs.existsSync(out)) return out;
    throw new Error('pdftoppm 执行完成但未生成 PNG');
  }

  if (hasMagick()) {
    execFileSync('magick', ['-density', dpi, `${pdfPath}[0]`, outPngPath], { stdio: 'ignore' });
    if (fs.existsSync(outPngPath)) return outPngPath;
    throw new Error('magick 执行完成但未生成 PNG');
  }

  throw new Error('未找到 PDF 渲染工具：请安装 Poppler(pdftoppm) 或 ImageMagick(magick)');
}

export { path };
