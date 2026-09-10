// UI 通道：Playwright 驱动真实前端页面（登录态注入 → 新建对话 → 上传 → 提示词 → 发送 → 生成PDF → 预览）。
import path from 'node:path';
import { chromium } from 'playwright';
import { ROOT, TIMEOUTS, WEB_BASE } from './lib/config.mjs';
import { loginApi, fetchPdfBuffer } from './api-fallback.mjs';
import { shot, saveChartCode } from './lib/artifacts.mjs';

/**
 * 建立浏览器会话。登录态通过接口登录后注入 sessionStorage，避免依赖登录表单（更稳）。
 * 如需覆盖登录 UI，可自行改为表单登录。
 */
export async function createSession({ headless = true, channel, creds } = {}) {
  const browser = await chromium.launch({ headless, channel: channel || undefined });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const { token, user } = await loginApi(creds);
  await context.addInitScript(
    ([t, u]) => {
      sessionStorage.setItem('token', t);
      sessionStorage.setItem('user', u);
    },
    [token, JSON.stringify(user || {})]
  );
  const page = await context.newPage();
  return { browser, context, page, token };
}

export async function closeSession(session) {
  if (!session) return;
  await session.context.close().catch(() => {});
  await session.browser.close().catch(() => {});
}

async function ensureModel(page, model) {
  if (model !== 'deepseek') return;
  const btn = page.locator('.model-btn');
  const text = await btn.innerText();
  if (!text.includes('Deepseek')) {
    await btn.click();
    await page.waitForFunction(
      () => document.querySelector('.model-btn')?.textContent?.includes('Deepseek'),
      undefined,
      { timeout: 5000 }
    );
  }
}

/**
 * 在 UI 中执行一个用例。
 * @param {import('playwright').Page} page
 * @param {string} token
 * @param {object} caseDef
 * @param {{model?: 'qwen'|'deepseek'}} [opts]
 */
export async function runCaseInUi(page, token, caseDef, opts = {}) {
  const model = opts.model || 'qwen';
  const r = {
    id: caseDef.id,
    ok: false,
    stage: '-',
    model,
    historyId: null,
    chartCode: '',
    pdfBuffer: null,
    uploaded: false,
    aiGenerated: false,
    codeGenerated: false,
    pdfCompiled: false,
    dialogVisible: false,
    screenshot: null,
    dialogScreenshot: null,
    error: '',
  };

  try {
    r.stage = '导航';
    await page.goto(`${WEB_BASE}/chart-generator`, { waitUntil: 'domcontentloaded', timeout: TIMEOUTS.NAV });
    await page.waitForSelector('.new-chat-btn', { timeout: TIMEOUTS.NAV });

    // 每例新建对话，隔离上下文
    await page.locator('.new-chat-btn').click().catch(() => {});
    await page.waitForTimeout(300);
    await ensureModel(page, model);

    // 上传数据文件（如有）
    if (caseDef.upload && caseDef.file) {
      r.stage = '上传';
      const abs = path.join(ROOT, caseDef.file);
      const nameNoExt = path.basename(abs).replace(/\.[^/.]+$/, '');
      const uploadResp = page.waitForResponse(
        (res) => res.url().includes('/api/datasets') && res.request().method() === 'POST',
        { timeout: TIMEOUTS.UPLOAD }
      );
      await page.setInputFiles('input[type="file"]', abs);
      const up = await uploadResp;
      if (up.status() !== 200) throw new Error(`上传接口 HTTP ${up.status()}`);
      await page.waitForFunction(
        (nm) =>
          Array.from(document.querySelectorAll('.selected-file-item .file-name')).some(
            (el) => el.textContent.trim() === nm
          ),
        nameNoExt,
        { timeout: TIMEOUTS.UPLOAD }
      );
      r.uploaded = true;
    }

    // 填提示词并发送（先挂响应监听，避免竞态）
    r.stage = '生成';
    await page.fill('.message-input textarea', caseDef.prompt);
    const chatResp = page.waitForResponse(
      (res) => /\/api\/chat$/.test(new URL(res.url()).pathname) && res.request().method() === 'POST',
      { timeout: TIMEOUTS.GENERATE }
    );
    await page.locator('.send-btn').click();
    const chat = await chatResp;
    const payload = await chat.json().catch(() => null);
    if (chat.status() !== 200 || !payload?.success) {
      throw new Error(`生成失败（HTTP ${chat.status()}）：${payload?.message || ''}`);
    }
    r.aiGenerated = true;
    r.historyId = payload.data.history_id;
    r.chartCode = payload.data.chart_code || '';
    if (!r.chartCode || !r.chartCode.includes('\\begin{tikzpicture}')) {
      throw new Error('返回内容不含可识别的 \\begin{tikzpicture} 代码');
    }
    r.codeGenerated = true;

    await page.waitForSelector('.chart-result', { timeout: TIMEOUTS.NAV });
    r.screenshot = await shot(page, `case${caseDef.id}-chat.png`);

    // 生成 PDF
    r.stage = '编译';
    const compileResp = page.waitForResponse(
      (res) => /\/api\/compile\/\d+$/.test(new URL(res.url()).pathname) && res.request().method() === 'POST',
      { timeout: TIMEOUTS.COMPILE }
    );
    await page.locator('.chart-result').last().getByRole('button', { name: '生成PDF' }).click();
    const comp = await compileResp;
    const compJson = await comp.json().catch(() => null);
    if (comp.status() !== 200 || !compJson?.success) {
      throw new Error(`编译失败（HTTP ${comp.status()}）：${compJson?.message || ''}`);
    }
    r.pdfCompiled = true;

    // 预览对话框
    const dialog = page.locator('.el-dialog').filter({ hasText: 'PDF预览' });
    await dialog.waitFor({ state: 'visible', timeout: TIMEOUTS.NAV }).catch(() => {});
    r.dialogVisible = await dialog.isVisible().catch(() => false);
    await page.waitForSelector('object[type="application/pdf"]', { timeout: 10000 }).catch(() => {});
    r.dialogScreenshot = await shot(page, `case${caseDef.id}-preview.png`);

    // 关闭对话框，避免影响下一例
    await page.keyboard.press('Escape').catch(() => {});

    // 通过鉴权接口取 PDF（离线渲染核对用）
    r.pdfBuffer = await fetchPdfBuffer(token, r.historyId);
    r.ok = true;
    r.stage = '-';
    return r;
  } catch (e) {
    r.error = e.message;
    r.screenshot = r.screenshot || (await shot(page, `case${caseDef.id}-fail.png`));
    return r;
  }
}

export { saveChartCode };
