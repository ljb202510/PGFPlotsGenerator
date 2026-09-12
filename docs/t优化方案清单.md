# AI 对话项目 LangChain + RAG 优化方向清单

> 目标：给「自然语言 + Excel/CSV → 生成 PGFPlots/TikZ → 编译 PDF」这条 AI 链路引入 **RAG / 检索增强生成**，用于 **Java 后端岗位面试展示**。
> 定位：不求堆技术，求「能讲清楚取舍、被追问不塌、落地真实痛点」。全文与源码路径以 `hello/` 实际为准。

---

## 0. 决策前提（先想清楚，避免为 RAG 而 RAG）

| 结论 | 说明 |
|---|---|
| **不重写后端为 Java** | 现有后端 13 路由 / 11 表 / 鉴权 / 编译 / PDF / 管理后台（见 `README.md：目录结构`）。为 RAG 全量重写工程量巨大、收益薄，性价比低。 |
| **RAG 与语言无关** | Node 生态有完整 LangChain.js，向量库（pgvector / Chroma / Qdrant）+ embedding 均可接入，不依赖 Java。 |
| **本项目不是典型问答型 RAG** | 传统 RAG 适合「检索私有文档做问答」；本项目的「知识」是 **PGFPlots/TikZ 代码模板**，本质更适合 **few-shot 示例库 + 结构化输出**。RAG 只是具象化的其中一种落法。 |
| **真正的价值落点** | 当前生成质量不稳定（见 `docs/log.md` 多条用例修复），AI 复用全量原始数据、无历史经验、无结构化产出。优化要治这个痛，而非堆概念。 |

---

## 1. 目录

- [§2 分层优化方向](#2-优化方向总分表)
- [§3 推荐主攻项 P1 模板知识库 + 检索增强](#p1)
- [§4 支撑项 P2：结构化输出 / 数据截断 / 缓存](#p2)
- [§5 Java 侧等价实现说明（面试加分）](#java)
- [§6 不建议做的方向（避免负性价比）](#no)
- [§7 验收与回归](#acceptance)

---

## 2. 优化方向总分表

> 工作量按相对规模「小 / 中」标注，不涉及全量重写级别的「大」。
> 排序逻辑：与「面试讲头」+「真实痛点」双重相关性强弱。

| 优先级 | 方向 | 对应章节 | 工作量 | 面试价值 |
|---|---|---|---|---|
| 🔴 P1 | 代码模板知识库 + 检索增强生成（few-shot RAG） | [§3](#p1) | 中 | 高（核心，能讲全链路） |
| 🟡 P2 | 结构化输出约束（JSON schema） | [§4](#p2) | 小 | 中 |
| 🟡 P2 | 大数据集 schema 提取 + 截断（替代全量拼入） | [§4](#p2) | 小 | 中 |
| 🟢 P3 | 生成缓存 + 历史命中复用 | [§4](#p2) | 小 | 低 |
| 🟢 P3 | 生成失败分析与自愈指标采集 | [§4](#p2) | 小 | 中（数据支撑面试） |

---

<a id="p1"></a>
## §3. P1 · 代码模板知识库 + 检索增强生成

**目标**：把「历史生成成功的模板」变成可检索的知识库，用户请求时召回最相关的 few-shot 示例拼进 prompt，提升生成质量与稳定性。这是整个项目的 AI 差异化亮点。

### 3.1 检索的实体与来源
- 来源：`generation_history` 表 + `backend/storage/history/{uid}/{id}.json`（`chat.js` 落库/落盘的历史成功案例，`has_chart_code=true`）。
- 可选补种：常见图表类型的**人工精标模板**（柱状/折线/饼/散点/面积/组合等，覆盖 `docs/testdata/` 的 30+ 用例类型）。
- 检索单元：语义切块 =「用户描述 + 数据集用途 + 图表类型 + 生成代码」。**不按字节硬切**（代码切块无召回意义）。

### 3.2 检索方案（轻量，可演进）
- **第一版（不引第三方向量库）**：MySQL 现有库建 `chart_template` 表（`id, title, chart_type, description, example_code, tags`）。检索用关键词/标签打分召回 Top-k（简单、零新依赖、可演示原理）。
- **演进版（接真实向量检索）**：用 embedding 模型把请求与模板向量化，Chroma（本地文件）或 pgvector（需换/加 Postgres）做相似度检索。面试可讲「先落关键词，再演进向量，说明为何这么分层」。

### 3.3 增强（RAG 的 "G"）
- 在 `chat.js` 的 `buildMessagesWithDataset` 的 system 提示中，注入召回的 Top-k 示例代码作为 few-shot 约束（示例代码只作风格/结构参考，不替代本次数据）。
- 明确「检索结果为空 → 回落原 prompt」，保证不降级现有能力。

### 3.4 涉及文件
| 文件 | 改动 |
|---|---|
| `backend/routes/chat.js` | 生成前调用检索；把召回的 few-shot 注入 `buildMessagesWithDataset`；记录检索命中与否到日志 |
| `backend/db.js` / 迁移 SQL | 新增 `chart_template` 表（沿用现有 MySQL） |
| `backend/routes/`（新增 `retrieval.js`） | 模板入库 / 关键词检索 /（演进）向量检索 API |
| `docs/log.md` | 新增 2026-09-12 条目记录本轮改动 |

### 3.5 面试表达（可直接讲）
> 「这个项目生成图表代码不稳定，我引入了检索增强生成：把历史成功的代码模板做成可检索知识库，用户请求时召回最相关的 few-shot 示例增强 prompt。第一版用 MySQL 关键词检索零依赖落地，层级上预留了向量检索演进——因为我清楚完整 RAG 在这个『代码模板』场景并不刚需，盲目上向量库是过度设计。考虑到面试 Java 岗，我把检索模块抽成独立服务，参考 LangChain4j + pgvector 给出 Java 侧等价实现。」

---

<a id="p2"></a>
## §4. P2 · 支撑项（小而实，配合 P1 讲得更立体）

### 4.1 结构化输出（JSON schema）
- **现状**：模型自由散文输出，靠 `extractChartCode()` 正则兜底提取（`chat.js`），不可靠。
- **改动**：提示词要求输出固定 JSON `{ "chart_type": ..., "code": ..., "summary": ... }`，后端先按 schema 解析，解析失败再走正则兜底。
- **价值**：让「提取不稳定」这个痛点从根上缓解，且面试可讲「LLM 结构化输出的工程约束」。

### 4.2 大数据集 schema 提取 + 截断
- **现状**：`readFileContent` 将整个 Excel/CSV 全量转文本拼入 prompt（上限 100MB），token 与成本失控。
- **改动**：数据量大时只送「列名 + 类型 + 前 N 行采样」+ 摘要，而非全量。
- **价值**：真实覆盖面大，面试可讲「上下文窗口与成本控制」。

### 4.3 生成缓存 / 历史命中
- 相同或高度相似请求直接复用历史成功模板，降低时延与成本，附日志可量化命中率。

### 4.4 生成失败指标采集
- 统计「生成为空 / 编译失败 / 提取失败」三类失败率（可借用 `api_log` 与现有系统日志），产出量化数据支撑面试（「我把 XX 用例首轮失败率从 X 降到 Y」）。

---

<a id="java"></a>
## §5. Java 侧等价实现说明（面向 Java 岗加分）

> 目的：证明「这套 AI 工程能力我能用 Java 落地」，不要求真写代码。

| 本项目的 Node 实现 | Java 等价（Spring 生态） |
|---|---|
| LangChain.js 检索增强 | **Spring AI** 或 **LangChain4j** 的 `RAG` / `EmbeddingStore` + `ChatModel` |
| MySQL 关键词检索 | 直接用 Spring Data JPA 查 `chart_template` 表（关键词/LIKE） |
| 向量检索演进 | **pgvector**（PostgreSQL 扩展）或 Milvus/Qdrant，Spring AI 有开箱支持 |
| embedding 接入 | `EmbeddingModel`（如 Qwen/DeepSeek embedding 或本地 bge 模型） |
| 模块化演示 | 检索服务做成独立 `@Service` + REST 接口，可单独起一个 Spring Boot 子模块起 demo |
| 结构化输出 | Spring AI 的 bean 输出 / LangChain4j 的 structured output，JSON 强绑定到 POJO |

**面试建议**：简历上本项目写 Node，但追加一句「认知迁移到 Java 生态（Spring AI / LangChain4j + pgvector）」，准备一段「怎么用 Java 实现同一套 RAG」的口述，被追问时不慌。

---

<a id="no"></a>
## §6. 不建议做的方向（避免负性价比）

| 方向 | 为什么不做 |
|---|---|
| 全量重写 Node 后端为 Spring Boot | 工程量巨大，只为一个 RAG 收益薄 |
| 双/多向量库并行检索 | 杀鸡用牛刀，面试容易被问崩 |
| 在 MySQL 里硬塞向量计算 | MySQL 无原生向量索引，性能与实现都别扭；演进应走 pgvector |
| 把「检索」做到项目所有接口 | 只对 AI 生成链路做，其余（认证/编译/后台）不动 |
| 为 RAG 硬造知识库内容 | 知识库只有模板库这一个天然来源，够了 |

---

<a id="acceptance"></a>
## §7. 验收与回归

- **功能**：带数据集生成、无数据集生成、编译 PDF 三条主链路不受影响（回归 `docs/manual-test-cases.md` 基线用例）。
- **RAG 生效**：构造一个模板库里有近似历史案例的请求，抓 prompt 日志确认 few-shot 已被注入；检索为空时确认回落原 prompt。
- **quality**：注入 few-shot 后生成的代码规范度（如不出现全角逗号等历史 bug）用例通过率不降反升。
- **数据**：调 `/list` 可返回结构化的 chart_template 记录；检索 API 返回 Top-k 及打分。

---

> 本清单聚焦 AI/RAG 技术线；功能与界面类优化沿用 `docs/plan.md`。两者互补不冲突。