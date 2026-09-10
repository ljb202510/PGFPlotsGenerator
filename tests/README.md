# 手动测试用例自动化套件（01–10）

把 `docs/manual-test-cases.md` §5/§6 的**基础用例 01–10** 变成一条命令可跑的回归测试：

```
登录 → 新建对话 → 上传数据 → 填提示词 → 发送 → AI 生成 → 生成PDF → 预览
```

- **UI 主通道**：Playwright 真实驱动前端页面（登录态由接口登录后注入，不依赖登录表单）。
- **失败重试**：失败自动切换到 **Deepseek-V4-Flash** 重试一次。
- **API 兜底**：UI 两次都失败时，绕过前端直连接口复现，用于区分「前端交互问题」与「生成/编译问题」。
- **逐例取证**：截图 / 生成的代码 / PDF / PDF 渲染 PNG，并自动生成 Markdown 报告。
- **凭据零落盘**：测试账号只从环境变量读取，不写入任何文件。

---

## 1. 前置条件

| 依赖 | 说明 |
|---|---|
| 后端 | `cd backend && npm run dev` → `http://localhost:3000`（需 `.env` 已配 `JWT_SECRET` 与 AI Key） |
| 前端 | `cd hello && npm run serve` → `http://localhost:8080` |
| 数据库 | MySQL 已运行且库 `X` 已有数据（**本套件不会执行 `1.sql`，不会清库**） |
| XeLaTeX | 本机已安装（用于服务端编译 PDF） |
| 浏览器 | 优先 Chromium：`npx playwright install chromium`；若不便下载，可用系统 Edge：加 `--channel msedge` |
| PDF 渲染 | `pdftoppm`（Poppler）或 ImageMagick `magick`；缺失时仍能跑，只是不产出 PNG |
| Node | ≥ 18（本机 v24，已内置 `fetch`/`FormData`/`Blob`） |

> Playwright 已在根 `package.json` 的 devDependencies 中（`playwright@^1.61.1`），无需额外安装 SDK。

## 2. 一键运行

**Windows CMD**

```bat
cd /d c:\Users\27710\Desktop\v2\PG\hello
set "TEST_EMAIL=你的账号"
set "TEST_PASSWORD=你的密码"
node tests/run-manual-cases.mjs
```

**PowerShell**

```powershell
cd c:\Users\27710\Desktop\v2\PG\hello
$env:TEST_EMAIL = "你的账号"
$env:TEST_PASSWORD = "你的密码"
node tests/run-manual-cases.mjs
```

**Linux/macOS/bash**

```bash
cd hello
TEST_EMAIL="你的账号" TEST_PASSWORD="你的密码" node tests/run-manual-cases.mjs
```

> 首次运行前建议先 `npx playwright install chromium`；或直接 `--channel msedge` 复用系统 Edge。
> 不想真跑、只看将要执行什么：`node tests/run-manual-cases.mjs --dry-run`（不联网、不登录、不调用 AI）。

## 3. 常用参数

| 参数 | 说明 |
|---|---|
| `--cases 01,02,05` \| `--cases 01-05` | 只跑指定用例（默认 01–10 全量） |
| `--model deepseek` | 主模型改用 Deepseek（默认 `qwen`，即 Qwen3.5） |
| `--no-retry` | 关闭「失败切 Deepseek 重试一次」 |
| `--api-only` | 跳过 UI，直接接口直连跑（最快，但不覆盖前端交互） |
| `--headed` | 有头模式（可看到浏览器操作，便于排查） |
| `--channel msedge` | 使用系统 Edge 而非下载 Chromium |
| `--dry-run` | 只打印用例清单与环境状态，不执行任何请求 |

示例：

```bat
node tests/run-manual-cases.mjs --cases 01-04 --dry-run
node tests/run-manual-cases.mjs --cases 01,02 --headed --channel msedge
node tests/run-manual-cases.mjs --api-only
```

## 4. 产物

| 路径 | 内容 |
|---|---|
| `docs/manual-test-report-<日期>.md` | 人读报告：总览表 + 逐例详情 + 证据链接 |
| `tests/results/<日期>/results.json` | 结构化结果（字段对齐文档 §7 表） |
| `tests/results/<日期>/caseNN-chart_code.tex` | 该例 AI 生成的 PGFPlots 代码 |
| `tests/results/<日期>/caseNN-hist<id>.pdf` | 编译产物 |
| `tests/results/<日期>/caseNN-hist<id>.png` | PDF 首页渲染图（**用于人工核对图表语义**） |
| `tests/results/<日期>/caseNN-chat.png` / `-preview.png` / `-fail.png` | 聊天区 / 预览弹窗 / 失败现场截图 |

## 5. 判定口径

- **机器判定**：`/api/chat` 返回的 `chart_code` 非空且含 `\begin{tikzpicture}` → 代码生成通过；`/api/compile/:id` 成功且能取到 PDF → 编译通过。
- **人工判定**：图表是否**符合语义**（中文标题/轴标签无乱码、数据与用例「数据预览」一致、量级单位匹配、有数值标注、图例不遮挡、非空白）——请打开 `results/` 中的 **PNG** 与截图核对；报告只列「验证要点」，不代替人眼结论。
- 结果：`通过` / `存疑`（能出图但个别要点不满足，或未捕获预览弹窗）/ `失败`（生成失败、无代码、编译失败、空气泡）。

## 6. 失败排查

| 现象 | 处理 |
|---|---|
| `✗ 后端不可达` | 先 `cd backend && npm run dev`；确认 `.env` 的 `JWT_SECRET` 与 AI Key |
| `✗ 前端不可达` | 先 `npm run serve`；确认 8080 端口 |
| `✗ 登录失败` | 检查 `TEST_EMAIL/TEST_PASSWORD`；确认账号存在（本套件不会注册账号） |
| `Executable doesn't exist ... chromium` | `npx playwright install chromium`，或加 `--channel msedge` |
| 生成超时/空回复 | 校园网/模型抖动；可 `--model deepseek` 或加大 `PG_GENERATE_TIMEOUT_MS`（默认 180000） |
| 想看真实浏览器操作 | 加 `--headed` |
| 只想快速验证链路 | `--api-only` |

可调环境变量：`PG_API_BASE`、`PG_WEB_BASE`、`PG_GENERATE_TIMEOUT_MS`、`PG_COMPILE_TIMEOUT_MS`、`PG_HTTP_TIMEOUT_MS`。

## 7. 单独使用 API 兜底通道

```bat
node tests/api-fallback.mjs --cases 01 --model deepseek
```

## 8. 注意事项

- **不要**把 `tests/results/` 提交到公开仓库：截图可能包含当前登录用户名。
- 本套件只读应用源码（`backend/`、`src/` 均未改动），仅在测试账号名下创建数据集与生成历史。
- 不会执行 `1.sql`、不会删除你的既有数据。
- 若需要覆盖「登录表单」的 UI 交互，可在 `ui-runner.mjs` 的 `createSession` 中改为表单登录（当前为接口登录后注入 `sessionStorage`，更稳定）。
