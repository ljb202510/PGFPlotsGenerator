# RAG 语料质量治理记录

> **日期**：2026-09-16　**范围**：批次 A–D（批次 D 待人工审阅后落地）
> **一句话结论**：把「生成成功即入库、从不判定」改为「**按判定者可靠性分级 + 入库与可召回解耦**」；
> 工程与验证均完成，**但语料质量仍有不可自动覆盖的盲区**（见 §6），不夸大。
> **口径**：所有数字均来自本机实跑（`mvn test` / `scripts/verify.cmd` / `RagCli` / 只读 SQL），可复算。

---

## 1. 问题：原来的入库门槛比"能编译"还低

原实现是「生成成功即入库」：

- 索引时机在**编译之前**——`ChatService.saveGenerationHistory` 中生成成功即调 `indexHistoryAsync`，
  而编译是用户随后**手动触发**的独立异步任务，两者无任何先后约束；
- 准入规则**只有一条**：`ChartCodeValidator.hasDuplicateSeries`（两个 `\addplot` 坐标集合完全相同），
  其类注释自述设计哲学为「刻意只做保守、可判定的检查：能判定的判不合格，判不准的一律放行，绝不误伤正常图」。

所以实际门槛是：**有代码（非空）通过 + 多系列坐标不完全重复通过**；编译成功、语法合法、语义与视觉正确**全部不检查**。

**为什么这是个问题**：用模型自己的输出当范例去教模型，本质是**闭环自举**——输出正确时强化正确，
输出错误时**同样强化错误**。缺的不是"要不要用历史记录"，而是**入库时有没有一个独立于生成者的判定者**。

---

## 2. 改造前基线（只读 SQL 实测，2026-09-16）

| 项 | 条数 | 说明 |
|---|---|---|
| `history` 向量总数 | **352** | |
| `template` 向量总数 | **7** | |
| **T1 内容里根本没有 tikz 代码** | **26** | `content NOT LIKE '%begin{tikzpicture}%'`：闲聊/乱输入被兜底提取，如「你是谁」「你的模型是什么」「1」「6」 |
| **T2 从未编译成功** | **83** | `generation_history.generation_path` 为空 |
| **T3 编译确认失败过** | **3** | `api_log.error_type='COMPILE_ERROR'` |
| **T4 无数据集** | **198** | `data_id IS NULL` |
| **T5 同一需求的重复旧副本** | **243**（剔除 T1 后）/ 260（SQL 粗算含 T1） | 同一句需求最多重复入库 **19 次** |

用户分布：`user4=159`、`user1=83`、`user16=72`、`user15=32`、`user11=5`。

**T5 是"RAG 为何量不出效果"的一个具体原因**：历史 `top-k=3`，而同一句需求最多重复入库 19 次 →
**3 个召回名额被同一需求的副本占满，实际只召回 1 条范例**；又因 `loadHistory` 原本只有 `LIMIT` 没有
`ORDER BY`（取到的是**最早写入的 N 条**），很可能取到的还是最旧那条。

### 2.1 一处根因判断的修正（重要）

改造前曾判断「闲聊被当作代码」是**兜底提取过松、会持续产生**。实测修正为：

- Java 版（2026-09-13 起）**非 tikz 代码只有 2 条**，且都在上线当天（09-13 13:03 ~ 23:13）；**09-14 之后无新增**。
- 因此 T1 的 26 条**主要是历史遗留**（早期版本产生，经 `rag_backfill` 批量回填进入向量库），
  **不是当前仍在持续产生的问题**。

本次仍然做了兜底收紧（§3 批次 A），因为它是**防御性加固**，且顺带修掉一个潜在 NPE（见 §5）。

---

## 3. 设计：三级质量分级

**核心判断**：语料的可靠性取决于**入库时是否存在一个独立于生成者的判定者**。判定者按可靠性排序：

| 判定者 | 能判什么 | 可靠性 | 本项目可用性 |
|---|---|---|---|
| 人工评审 | 图/数据/语义正确性 | 最高（唯一能判语义） | 可用，不可扩展 |
| 编译成功 | 仅语法自洽 | 低 | ✅ 已有（`generation_path`） |
| 静态规则检测 | 仅结构违规 | 中低 | ✅ 已有（`violationsForIngest`） |
| 多次生成一致性 | 稳定性 | 中 | 未实现 |
| 另一模型评审 | 语义 | 不可靠（同源） | 未采用 |
| ~~用户反馈~~ | — | — | **不适用**：`feedback` 表是功能建议/界面/bug 类通用反馈，**无 history 关联、无评分** |

据此把「**入库**」与「**可召回**」解耦：

| 等级 | 判定者 | 是否召回 | 实现 |
|---|---|---|---|
| `golden` | 人工定义真值 | ✅ | 模板分区（7 条） |
| `verified` | 编译成功 **且** 静态零违例 | ✅ | `--rag-cli=verify:<userId>` 离线定级 |
| `unverified` | 无 | ❌（等价于已下架，**但不删数据、完全可逆**） | 默认等级 |

**过滤位置**：仅**历史分区**按 `app.rag.min-quality` 过滤（默认 `verified`）；
模板分区全为 `golden`、**恒不参与过滤**；既有「分区独立 Top-K（历史 3 / 模板 2）」已实现模板优先，**未引入跨等级加权**。
**兜底**：过滤后历史分区为空时自动回退为不过滤并记日志，**绝不把检索变哑**。

---

## 4. 改造内容（逐批）

### 批次 A：准入规则集 + 提取兜底收紧

| 改动 | 文件 |
|---|---|
| 新增 `violationsForIngest`：`NO_TIKZ`（必须含 tikz 画布）+ R1/R2/R3/R5/R6/R8/R9/R12；**显式排除**已被编译前预处理兜底的 R7/R10/R11（纳入会误杀有效案例） | `util/ChartCodeValidator.java` |
| 两条入库接入点改用规则集 | `service/rag/RagService.java`、`tools/RagCli.java`（backfill） |
| **提取兜底收紧**：结果必须含 tikz 画布，否则视为提取失败。**两处都改**——`ChatService` 的三出口汇聚点 + `saveGenerationHistory` 内那条**独立的兜底提取**（只改一处不生效） | `service/ChatService.java` |
| 顺带修掉潜在 NPE：`finalChartCode` 为 null 时 `chart_code_length` 会抛异常 | 同上 |

> **与计划的偏离**：原计划把收紧校验放进 `ChartCodeExtractor` / `StructuredOutputParser`，
> 核实后发现那会**连带影响 `attemptOn` 的纠正重试判定**（会让"你是谁"这类非图表提问也触发一次重试），
> 故改放在 `ChatService` 的汇聚点。

### 批次 B：迁移 + 基线取证 + 分类报告

| 改动 | 说明 |
|---|---|
| 迁移 `migrations/alter_rag_vector_quality.sql` | 新增 `quality`（ENUM，默认 `unverified`）与 `data_source`；回填 `template→golden`、`history→unverified` |
| `RagCli.purge` 改为**只读分类报告** | 输出 T1 / T5 条数；**默认不删除任何数据** |
| `RagCli.purge:apply` | 显式确认后才真正删除（本机未执行） |

> **与计划的偏离（重要）**：原计划是「直接删除 T1」。实测 T5 达 **243 条（占历史分区 69%）**，
> 破坏性过大，且用户此前**并未确认**删除方式。因此改为**零删除方案**：
> `unverified` 天然不召回 = 等价于已下架，但**保留可回溯、完全可逆**。
> 删除能力保留在 `purge:apply`，由使用者显式决定。

### 批次 C：分级基础设施 + 检索过滤

| 改动 | 文件 |
|---|---|
| 新增等级常量与 `recallable(quality, minQuality)` 判定（纯静态、无 Spring 依赖） | `service/rag/RagQuality.java`（新增） |
| 实体新增 `quality` / `dataSource` 字段 | `entity/RagVector.java` |
| 读写等级与来源；新增 `markHistoryQuality` / `markHistoryDataSource`；**`loadHistory` / `loadAllHistory` 补 `ORDER BY vector_id DESC`**（修「取最早 N 条」缺陷） | `service/rag/VectorStore.java` |
| 历史分区按 `minQuality` 过滤 + **空召回回退**；模板分区不受影响 | `service/rag/Retriever.java` |
| 新增 `min-quality` 配置（默认 `verified`，环境变量 `RAG_MIN_QUALITY` 可覆盖，设为 `unverified` 即一键回退） | `config/AppProperties.java`、`application.yml` |
| 新增 `verify:<userId>` 离线定级模式（**纯读取 + 更新，不调用大模型**）；`seed` 写入置 `golden` | `tools/RagCli.java` |

**离线定级的判据**：① 编译成功（`generation_history.generation_path` 非空——该字段**只在编译成功时写入**，
`api_log` 只在失败时落库，故不能用 api_log 判定成功）② `violationsForIngest` 为空。
两者都**独立于模型自评**。**幂等**：每次先全部重置为 `unverified` 再重新判定，可重复执行。
**去重**：同一 `embed_text` 只保留 `vector_id` 最大的一条为 `verified`，避免副本占满名额。

### 批次 D：模板库扩容（**待人工审阅，未落地**）

候选清单见 `docs/rag/rag-l1-candidates.md`：现有 7 条仅覆盖 7 类图型，实测验证过的有 20+ 类。
拟从 checklist 实测通过用例（`user_id=16` 的 466–496，**全部编译成功**）中选取 15 条补齐缺口
（面积图 / 水平条形图 / 直方图 / 对数轴 / 平滑曲线 / 百分比堆积柱 / 多分类密集 / 多系列错开 /
负值柱 / 单数据点 / 超长标签 / 极值悬殊 / 退化值 / 分组柱 / 小数百分比），扩至约 22 条。

---

## 5. 逐批验证结果（全部实跑）

| 验证项 | 结果 |
|---|---|
| `mvn test` | **全绿**：48（改造前）→ **62**（批次 A，+14）→ **67**（批次 C，+5） |
| 零误杀断言 | 已覆盖：R7（全角逗号）/ R10（直方图缺 ybar）/ R11（ymax 过小）**均不命中** |
| `scripts/verify.cmd` | **PASS=42 FAIL=0 WARN=0**（批次 A 后与最终各跑一次，均无回退） |
| 真实生成链路 | history **502**，代码 442 字符、**含 tikz**、通道 qwen —— 证明收紧校验**未误伤正常提取** |
| 迁移回填 | `history` unverified 352 / `template` golden 7 ✓ |
| 离线定级（`verify:1`） | 83 条 → 跳过 6（从未编译成功）+ 6（命中准入判据）→ 合格 72 → **去重后 verified 20 条** |
| 来源标记 | 该账号 verified 20 条全部 `no-dataset` ✓ |
| `rag_demo` 召回 | **非空**：`history#502`（0.83）/ `#367`（0.72）/ `#205`（0.71）+ `template#7`（0.56）/ `template#1`（0.55）→ 分级过滤生效且**模板分区未受影响** |
| `purge` 分类报告 | T1 **26** 条 / T5 **243** 条 / 合计 269 条 / 扫描 352 条；**未删除任何数据** |

### 5.1 一次误判的排查过程（记录备查）

首次跑 `verify.cmd` 出现 `PASS=39 WARN=1`（第 5 节编译链路失败，错误为「该历史记录没有可编译的图表代码」），
一度怀疑是收紧校验造成的回归。**排查结论：不是回归，而是测试数据前置条件不满足。**

- `verify.js` 第 5 节取 `records[0]`（当前用户**最新一条**历史），而运行前最新的是 **499 号**
  ——2026-09-15 23:37 产生、`generation_code` 为空（**早于本次改动**）；
- 用真实生成补齐前置条件（history 502，含 tikz）后重跑，**回到 PASS=42 FAIL=0 WARN=0**。

**结论**：`verify.js` 第 5 节依赖「最新一条历史有可编译代码」这一隐含前置条件，属**测试脆弱性**，
非产品缺陷。已在此记录，避免后续误判。

---

## 6. 剩余盲区（**必须如实理解，不要高估本机制**）

1. **"编译成功 + 静态零违例" ≠ 语义/视觉正确**。反例都在本项目实测中发生过：
   三个负值柱被挤出可视区（图上不可见）、14 根柱顶数字互相压盖、X 轴长标签重叠——**编译全部成功**。
   其中 **R4（数值与坐标轴量纲一致）与视觉重叠没有自动判据**，只能人工抽检。
2. **项目没有用户级单次生成质量信号**。`feedback` 表是功能建议/界面/bug 类通用反馈，
   **无 history 关联、无评分**，不能作为"这张图对不对"的依据。
3. **示意数据不可自动识别**。`data_source` 只能标 `dataset` / `no-dataset`；
   「使用公开统计数据」与「模型自拟示意数据」**都不带数据集**，无法区分。
4. **"编译成功"的判定含保守偏差**。用 `generation_path` 非空判定；而 `ErrorTypes.COMPILE_ERROR`
   同时覆盖「LaTeX 报错」「xelatex 执行异常」「任务被中断」，**环境类失败也会导致 `generation_path` 为空**
   → 会被保守地判为"未验证"（**方向安全：不会误升级，但会少收录**）。
5. **模板库仍只有 7 条**，冷门图型召回为空（批次 D 候选清单待审阅后落地）。
6. **只对账号 1 做了离线定级**（其余账号 4/15/16 共 332 条仍为 `unverified`，不参与召回）。
   覆盖面取决于"给哪些账号定级"这一决策。

---

## 7. 如何复现 / 复跑

```bat
:: 1) 应用迁移（新增 quality / data_source 并回填）
mysql -u root -p000 -D X < hello\migrations\alter_rag_vector_quality.sql

:: 2) 构建
cd hello\spring-backend && scripts\build.cmd

:: 3) 脏语料只读报告（不删除任何数据）
java -jar target\pgfplots-backend-1.0.0.jar --rag-cli=purge
::    如需真正删除：--rag-cli=purge:apply

:: 4) 离线定级（按账号；纯读取+更新，不调用大模型）
java -jar target\pgfplots-backend-1.0.0.jar --rag-cli=verify:1

:: 5) 召回验证
java -jar target\pgfplots-backend-1.0.0.jar "--rag-cli=demo:画一个柱状图对比各车间产量:1"

:: 6) 回归
mvn test
scripts\verify.cmd
```

**回退方式**：把 `app.rag.min-quality` 设为 `unverified`（或环境变量 `RAG_MIN_QUALITY=unverified`）即等价于
「不过滤」，行为回到改造前；`verify` 可重复执行、可随时重算。

---

## 8. 下一步

1. **批次 D**：审阅 `docs/rag/rag-l1-candidates.md`，确认入选清单后把模板库扩到约 22 条并重排 `refId`。
2. **是否扩大定级范围**：目前只定级了账号 1。若要覆盖更多账号/作为演示主体，需明确范围后重跑 `verify:<userId>`。
3. **提示词 / 检索消融实验（延后）**：检验「按需检索范例」能否替代提示词中的静态模板段。
   在语料分级完成之前做该实验，结论会被脏语料污染，故先治理后测量。
4. **长期**：若要真正提升 `verified` 的可信度，缺的是**语义级判据**（人工抽检或视觉校验），
   而不是再加自动规则——自动规则已经到边界了。
