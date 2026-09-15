# 测试资产总览

> 版本：v1.2　日期：2026-09-14
> 盘点项目内所有测试脚本、测试文件、测试数据与测试文档的位置及用法。

## 1. 测试资产清单（索引）

| 类别 | 位置 | 说明 |
|------|------|------|
| 后端单元测试（JUnit 5） | `spring-backend/src/test/` | 编译/校验/解析/检索等纯逻辑单测 |
| 后端冒烟验证脚本 | `spring-backend/verify.js` | Node 接口级冒烟，覆盖核心 HTTP 链路 |
| 后端离线评估集 | `spring-backend/eval/` | E1 评估：提示词→chat→编译→静态违例检测 |
| 测试数据 | `docs/testdata/` | 25 个数据文件（CSV+XLSX），对应用例 01–32 |
| 手动测试用例 | `docs/manual-test-cases.md` | 核心手动测试（用例 01–32） |
| 缺陷修复记录 | `docs/log.md` | 各批次实测问题与修复记录 |

## 2. 后端单元测试（JUnit 5）

位置：`spring-backend/src/test/java/com/pg/pgfplots/`

- `util/LatexCompilerTest.java` — XeLaTeX 编译相关逻辑
- `util/ChartCodeValidatorTest.java` — 图表代码校验规则
- `util/ChartCodeExtractorTest.java` — 图表代码提取
- `util/StructuredOutputParserTest.java` — 结构化输出/JSON 解析
- `service/rag/RecrieverTest.java` — RAG 检索

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

## 4. 后端离线评估集

位置：`spring-backend/eval/`

- `eval.mjs` — 主脚本：逐用例 `POST /api/chat` → `POST /api/compile` → 轮询任务终态，并对生成代码跑静态违例检测
- `violations.mjs` — R1–R8 静态违例检测（R4 量纲一致性除外）
- `cases.json` — 评估用例集

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
| `docs/manual-test-cases.md` | 手动测试用例全集（01–10 基础 + §9 进阶 11–32），含前置准备、通用流程、预期结果、实测结论 |
| `docs/log.md` | 各批次（2026-09-09 起）实测问题与修复记录，按「用例 XX · 问题」结构 |
| `docs/development.md` / `docs/deployment.md` / `docs/architecture.md` | 开发、部署、架构说明 |
| `docs/plan-AI-落地清单.md` | AI 能力落地清单 |

## 7. 前端测试说明

前端（Vue 3）当前**无自动化测试**：`package.json` 仅含 `serve / build / lint` 脚本；已引入 `playwright` 依赖但尚无测试文件或配置。前端的质量保障依赖 `docs/manual-test-cases.md` 手动回归。