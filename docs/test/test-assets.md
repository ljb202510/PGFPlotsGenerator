# 测试资产总览

> 版本：v1.2　日期：2026-09-14
> 盘点项目内所有测试脚本、测试文件、测试数据与测试文档的位置及用法。

## 1. 测试资产清单（索引）

| 类别 | 位置 | 说明 |
|------|------|------|
| 后端单元测试（JUnit 5） | `spring-backend/src/test/` | 编译/校验/解析/检索等纯逻辑单测 |
| 后端冒烟验证脚本 | `spring-backend/verify.js` | Node 接口级冒烟，覆盖核心 HTTP 链路 |
| 编译提交延迟实测 | `spring-backend/measure-submit-latency.mjs` | 量 `POST /api/compile/{id}` 的提交耗时（异步契约 G1），可一键复现 |
| 后端离线评估集 | `spring-backend/eval/` | E1 评估：提示词→chat→编译→静态违例检测 |
| 测试数据 | `docs/testdata/` | 25 个数据文件（CSV+XLSX），对应用例 01–32 |
| 手动测试用例 | `docs/test/manual-test-cases.md` | 手动测试全集（01–10 基础 + 11–32 进阶） |
| 用例清单（精简版） | `docs/test/manual-test-cases-checklist.md` | 32 例，17 号放弃 → **有效 31 例**；含明细与记录表 |
| 业务用例批量执行器 | `spring-backend/run-cases.mjs` | 跑上面 32 例（`--compile --rag=on/off --tag=…`），产物落 `spring-backend/test/results/` |
| RAG on/off 实跑对比 | `docs/test/eval-rag-onoff-comparison-32cases.md` | 31 例 × 2 轮（2026-09-16）：逐例数字、违例分布、62 张目视评分 |
| 缺陷修复记录 | `docs/log.md` | 各批次实测问题与修复记录 |

## 2. 后端单元测试（JUnit 5）

位置：`spring-backend/src/test/java/com/pg/pgfplots/`

- `util/LatexCompilerTest.java` — XeLaTeX 编译相关逻辑
- `util/ChartCodeValidatorTest.java` — 图表代码校验规则
- `util/ChartCodeExtractorTest.java` — 图表代码提取
- `util/StructuredOutputParserTest.java` — 结构化输出/JSON 解析
- `service/rag/RetrieverTest.java` — RAG 检索

运行（在 `spring-backend` 目录）：

```bash
mvn test
```

依赖：纯逻辑，无需启动数据库/服务。

## 3. 后端冒烟验证脚本

位置：`spring-backend/verify.js`（Node，零第三方依赖）

覆盖：鉴权 / 管理后台 / 用户侧查询 / 数据集增删改查 / XeLaTeX 编译 / PDF 鉴权流式返回。

运行（在 `spring-backend` 目录）：

```bash
node verify.js               # 自动启动后端并全量验证
node verify.js --no-start    # 后端已在运行时只做接口验证
```

依赖：本机 MySQL 已启动且库 `X` 已初始化；编译用例需要 XeLaTeX。

## 3.1 编译提交延迟实测

位置：`spring-backend/measure-submit-latency.mjs`（Node，零第三方依赖）

用途：量「提交即返回 task_id」的**提交耗时**，对照改造前同步等待编译最长 30s（`LATEX_TIMEOUT_MS`）。

运行（在 `spring-backend` 目录，后端已启动；账号需名下有 ≥ `--count` 条带代码的历史）：

```bash
node measure-submit-latency.mjs --email=<user@example.com> --password=<pwd> --count=4 --rounds=3
# 可选：--base=http://localhost:3000 / --timeout=10000 / --no-wait
```

产出：逐轮每请求耗时 + min/median/p95/max 汇总 + 任务终态（证明任务不被静默丢弃）。
2026-09-16 复测：12 次提交 min 7 / median 12 / p95 24 / max 24 ms，12/12 success（详见 `docs/log.md` 2026-09-16）。

## 4. 后端离线评估集

位置：`spring-backend/eval/`

- `eval.mjs` — 主脚本：逐用例 `POST /api/chat` → `POST /api/compile` → 轮询任务终态，并对生成代码跑静态违例检测
- `violations.mjs` — 静态违例检测，当前覆盖 **R1–R3、R5–R9、R12**（R4 量纲一致性、R10 直方图 ybar、R11 ymax 余量刻意不做静态检测：R10/R11 已被编译前预处理确定性修复，检测会恒为零违例、失去区分度）
- `cases.json` — 评估用例集（**16 例**）

运行（在 `spring-backend` 目录，后端已启动）：

```bash
node eval/eval.mjs --tag=rag-off
node eval/eval.mjs --tag=rag-on --base=http://localhost:3000
node eval/eval.mjs --tag=v2-qwen-rag-on --model=qwen   # 指定模型通道（默认 qwen）
```

产出：控制台汇总表 + `eval/results/<tag>.json`。

## 5. 测试数据

位置：`docs/testdata/`，25 个文件，对应用例编号：

- **01–08 基础用例**：`01-line-sales.csv`、`02-bar-workshop.csv`、`03-pie-share.xlsx`、`04-scatter-ad-sales.csv`、`05-groupedbar-products.xlsx`、`06-multiline-channels.csv`、`07-stackedbar-regions.xlsx`、`08-budget-city.csv`
- **11–32 进阶用例**：`11-area-greening.csv` … `32-equal-quarters.csv`（含 `25-provinces-gdp.csv`、`27-negative-profit.csv`、`29-longlabel-regions.xlsx` 等）

## 6. 测试文档

| 文档 | 说明 |
|------|------|
| `docs/test/manual-test-cases.md` | 手动测试用例全集（01–10 基础 + §9 进阶 11–32），含前置准备、通用流程、预期结果、实测结论 |
| `docs/test/manual-test-cases-checklist.md` | 精简版清单（32 例 / 17 号放弃 → 有效 31），供 `run-cases.mjs` 批量回归 |
| `docs/test/eval-rag-onoff-comparison-32cases.md` | RAG on/off 31 例实跑对比（2026-09-16）与目视评分 |
| `docs/log.md` | 各批次（2026-09-09 起）实测问题与修复记录，按「用例 XX · 问题」结构 |
| `docs/development.md` / `docs/deployment.md` / `docs/architecture.md` | 开发、部署、架构说明 |
| `docs/plan-AI-落地清单.md` | AI 能力落地清单 |

## 7. 前端测试说明

前端（Vue 3）当前**无自动化测试**：`package.json` 仅含 `serve / build / lint` 脚本；已引入 `playwright` 依赖但尚无测试文件或配置。前端的质量保障依赖 `docs/test/manual-test-cases.md` 手动回归。