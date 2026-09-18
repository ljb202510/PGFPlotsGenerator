# PGFPlotsGenerator 优化路线图（整合版）

> **本文件是唯一的优化路线图**，整合 `c优化方向评估.md`、`t优化方案清单.md`、`w优化方向清单.md` 三份文档，
> 并按**已完成的 Java 重构（`spring-backend/`）**重新校准。
> 日期：2026-09-13　｜　前置：第一阶段「技术栈纯迁移」已完成并通过验收（`verify.cmd` PASS=27 / FAIL=0）

---

## 0. 三句话结论

1. **第一阶段已完成**：Node/Express → Spring Boot + MyBatis-Plus 全量重写，13 路由 / 11 表 / 统一 `{success,data,message}`，本文件规划**第二阶段起的优化项**。
2. **三份原文档的前提已过期**：「不重写 Java / 在 Node 侧改造」的结论作废；其中的 Node 目录、`backend/routes/*.js`、openai SDK、mysql2、LangChain.js 等一律替换为 **Java 等价物**（`service/`、`client/`、`util/`）。
3. **只做 2–3 件事的话，推荐**：**RAG 检索增强 + Prompt 工程化（含结构化输出）+ 编译任务队列化**（沿用 w 文档组合，理由见 §4）。

---

## 1. 与三份原文档的关系（保留 / 修正 / 废弃）

| 原文档 | 保留（有价值） | 修正或废弃 |
|---|---|---|
| `c优化方向评估.md` | RAG 的**取舍逻辑**：不引 LangChain、暴力余弦、MySQL 存向量、按 `user_id` 隔离、失败静默降级、召回演示脚本、`dim`/`model` 一致性校验 | 实现栈（CommonJS / `openai` SDK / `services/rag/*`）、`backend/routes/chat.js` 两处接入点 → 全部改写为 Java |
| `t优化方案清单.md` | P1/P2/P3 分层、结构化输出、大数据集截断、生成缓存、失败指标采集、**Java 侧等价实现表**、不建议方向、验收清单 | §0「不重写后端为 Java」、§6「全量重写性价比低」→ **已作废**（重写已完成） |
| `w优化方向清单.md` | 四类优化清单（AI / 评估 / 工程 / 可观测安全）与推荐三件套 | 与 Node 相关的落点（`child_process.exec`、`package.json` 等）→ 改 Java |

> 建议：三份原文档**保留为素材**，但以本文件为准，避免多份并存产生歧义。

---

## 2. 现状基线（可直接复用的落点）

| 能力 | 现有落点（`hello/spring-backend/`） |
|---|---|
| 提示词集中管理 | `util/PromptTemplates.SYSTEM_PROMPT`（已逐字对齐原版） |
| 提示词组装 + 数据集注入 | `service/ChatService.buildSystemPrompt` |
| 多通道调用 + 降级链 | `client/LlmClient.chat`（`/chat/completions`，三通道共用）、`ChatService.generateWithFallback`（reasoning 兜底 → 同模型纠正重试 → 硬错误按 `qwen → siliconflow → deepseek` 接力，`enabled=false` 整层跳过） |
| 代码提取 | `util/ChartCodeExtractor`（围栏优先 + 裸 `tikzpicture` 兜底） |
| 编译 + 危险序列校验 | `service/CompileService.compile`、`util/LatexCompiler`（`ProcessBuilder` 30s + `safeCleanup`） |
| 系统日志 | `util/SystemLogWriter` → `system_log`（副作用式，不抛异常） |
| 调用日志 | `api_log`（`call_status` success/failed、`call_error`） |
| 历史 / 数据集 | `generation_history`（`generation_code`）、`data_file` |
| 统计聚合 | `mapper/AdminMapper.xml` + `service/AdminLogService`（已有 api-stats / health / 分类统计骨架） |
| 配置挂点 | `config/AppProperties`（`jwt` / `storage` / `latex` / `llm.Provider` / `mail`） |
| 统一响应 / 异常 | `common/Result`、`common/BusinessException`、`common/GlobalExceptionHandler` |

**结论**：优化项几乎都能挂在**已有类**上改造，不需要新建模块骨架。

---

## 3. 优化项总表（全部 19 项）

> 优先级定义：**P1 = 高面试价值 + 小/中工作量（先做）**；P2 = 中价值或中工作量；P3 = 低价值或大工作量。
> 工作量：小（≤0.5 天）/ 中（1–2 天）/ 大（≥3 天）。价值按「能否支撑一段完整面试叙事」评估。

| 编号 | 方向 | 具体做什么 | 面试可讲 | 工作量 | 价值 | 优先级 |
|---|---|---|---|---|---|---|
| A1 | RAG 检索增强 | 历史需求 + 模板库向量化，生成前召回 Top-k 作 few-shot 注入 | 向量化/余弦检索/召回评估/为何不上向量库 | 中 | 高 | **P1** |
| A2 | Prompt 工程化 | 模板集中 + 版本号 + few-shot 示例区 + 失败重试 | 提示词版本管理、约束解码 | 小 | 高 | **P1** |
| A3 | 结构化输出 | 要求 `{chart_type, code, summary}` JSON，解析失败回退正则兜底 | 结构化输出的工程约束 | 小 | 中 | **P1** |
| A4 | 大数据集截断 | 大文件只送「列名+类型+前 N 行采样」，替代全量拼入 | 上下文窗口与成本控制 | 小 | 中 | P2 |
| A5 | 多模型路由 | 按复杂度/长度选模型 + 失败自动降级（降级已有） | 降级策略、成本与延迟权衡 | 中 | 中 | P2 |
| A6 | 流式输出 | SSE 逐步返回生成内容 | 长请求体验优化 | 中 | 低 | P3 |
| E1 | 离线评估集 | 固定一批 prompt 做回归，输出一次通过率 | 有评估意识、可量化迭代 | 中 | 高 | **P1** |
| E2 | 失败归类统计 | 空回复 / 围栏不闭合 / 编译失败 / 上游错误 分类计数 | 定位问题能力 | 小 | 中 | P2 |
| E3 | 质量看板 | 通过率 / 耗时分位数可视化（复用 ECharts 监控页） | 数据驱动迭代 | 中 | 中 | P2 |
| G1 | 编译任务队列化 | 异步任务 + 状态查询，替代同步阻塞 30s | 异步任务、状态机、超时重试 | 中 | 高 | **P1** |
| G2 | 编译并发控制 | XeLaTeX 重进程加并发上限（`Semaphore`） | 资源隔离、背压 | 小 | 中 | P2 |
| G3 | 生成缓存 | 完全相同的 prompt+数据集命中历史复用 | 缓存键设计 | 小 | 中 | P2 |
| G4 | 限流 | `/api/chat` 按用户限流防刷 | 限流算法 | 小 | 中 | P2 |
| G5 | 前端 api 封装层 | 抽统一 request 层 + 全局错误处理 | 前端工程化 | 中 | 低 | P3 |
| G6 | 路由守卫 | 14 条路由加全局守卫 | 前端权限控制 | 小 | 低 | P3 |
| O1 | 链路耗时拆解 | LLM / 编译 / IO 分段计时并聚合 P95 | 性能剖析 | 中 | 中 | P2 |
| O2 | 上传内容校验 | 扩展名 + 魔数校验（现只限 100MB） | 输入校验 | 小 | 中 | P2 |
| O3 | 编译沙箱 | 进程隔离 / 资源限制 | 安全边界 | 大 | 低 | P3 |
| O4 | 启动配置校验 | 必填配置缺失即启动失败 + 明确提示 | 配置治理 | 小 | 中 | P2 |

---

## 4. 优先级与分批计划

> 排序依据（沿用 w 文档思路）：**前两批直接对标 AI 应用岗 JD 高频词，且都有「前后对比数据」可讲**。

| 批次 | 主题 | 包含项 | 为什么这样切 |
|---|---|---|---|
| **批次 0（已完成）** | 技术栈纯迁移 | Java 重构全量 | `verify.cmd` 27 项全绿，作为后续一切优化的回归基线 |
| **批次 1** | AI 亮点（一个主题做完） | **A1 + A2 + A3 + E1** | 四者都改 `ChatService` / `PromptTemplates` / 评估脚本，耦合度高，一起做才自洽；E1 正好用来证明 A1/A2 的效果。**状态：主链路（A1+A2+A3+CLI）已完成并于 2026-09-13 通过全部实测验收（硅基流动 embedding：seed 7 模板幂等 / backfill 181 条 0 失败 / 召回 0.71 vs 无关 0.43 区分清晰 / 活体生成 few-shot 注入 + chart_type=bar + prompt_version 落库，详见施工图 §9 执行记录）；E1（T12）已完成——rag-off/rag-on 各跑 10 条，首轮通过率均 1.0，见 §12 回填** |
| **批次 2** | 工程硬功 | **G1 + G2 + O1** | 都围绕编译与可观测；把项目从「能跑」讲到「能扛」。**状态：已完成并于 2026-09-14 通过实测验收**——`POST /api/compile/{id}` 改为立即返回 `task_id`（4 任务并发提交响应 14–19ms，改造前最长阻塞 30s）+ `GET /api/compile/task/{taskId}` 轮询；`Semaphore` 限流经 4 任务压测证明同一时刻 XeLaTeX 并发 ≤ `max-concurrency=2`；`api_log.duration_ms` 落库 + `api-stats` 真实 avg/P95/sample_count（与 DB 查询一致，替换硬编码 245/420）；`verify.cmd` **PASS=29 FAIL=0**。重启语义与队列满 503 属约定只给复现步骤，详见 `docs/log.md`「批次2 落地」 |
| **批次 3** | 评估与质量 | **E2 + E3** | 复用 `api_log` / `system_log` 与现有监控页，几乎白捡。**状态：代码与断言已完成并通过回归（2026-09-14，`verify.cmd` PASS=31 / FAIL=0）**——E2 失败归类（`api_log.error_type` 7 类枚举 + 补齐「空回复未落库」「提取不到代码记 success」两个缺口，编译类失败只在 `CompileTaskService` 落库防双计）、E3 质量看板（监控页新增失败分类分布 + 按日 avg/P95 双折线 + 耗时卡片，含空态三规则；耗时指标只统计生成链路，编译耗时不混算）、评估集 V2（`eval/violations.mjs` 七类静态违例检测 + `zero_violation_rate`，原 10 条用例保持不变并追加 6 条进阶/歧义用例；R4 量纲一致性明确不做静态检测）。**人工验收（2026-09-14 完成）**：评估集 V2 rag-on 轮 16 用例全部通过（生成/可编译/首轮通过率均 1.0），**零违例率 1.0**（修正 R8 启发式误报后；V2 用例在"零违例"维度仍饱和）；前端质量看板两张图与三条空态规则经浏览器自动化实测通过（卡片数值与 SQL 交叉验证一致）。**唯一未闭环**：rag-off 轮因 DeepSeek 账户余额不足作废（与 RAG 开关无关），需充值后重跑。详见 `docs/log.md`「批次3 落地 §8」与 `docs/plan-AI-批次3-执行方案.md` |
| **批次 4** | 成本与健壮性 | **A4 + G3 + G4 + O2 + O4** | 都是「小工作量、中价值」的补强项，可一起收尾 |
| **批次 5（可选）** | 体验与进阶 | **A5(增强) + A6 + O3 + G5 + G6** | 低优先或大工作量，按时间决定做不做 |

**每批次结束都要跑一遍 `verify.cmd`，保证 27 项基线不回归。**

---

## 5. RAG 检索增强方案（A1，含选型建议）

### 5.1 选型建议：**第一版「MySQL 存向量 + Java 侧暴力余弦」**（自研）

三个候选方案对比：

| 方案 | 新增依赖 | 工作量 | 面试可控度 | 说明 |
|---|---|---|---|---|
| ① MySQL 关键词/标签召回 | 无 | 小 | 中 | 最省事，但「检索增强」含金量弱，容易被追问「这也算 RAG？」 |
| **② MySQL 存向量 + Java 暴力余弦** | **无** | **中（≈200 行）** | **高** | **推荐**：复用现有库与模型通道，每行可解释 |
| ③ Spring AI / LangChain4j + pgvector | PostgreSQL / 框架 | 大 | 低 | 需另起 PG 运维；框架黑盒，被追问底层容易穿帮 |

**为什么推荐 ②（性价比论证）**：
1. **零新中间件**：复用现有 MySQL（`X`）与 HikariCP，部署/运维成本为 0；pgvector 要另起 PostgreSQL，收益与代价不匹配。
2. **规模匹配**：历史+模板数百~数千条 × 1024 维，单次检索 `O(N×D)` 约百万级浮点乘加，**毫秒级**；上万条再演进 ANN/HNSW。
3. **面试可控**：embedding 客户端 / 向量存取 / 余弦检索 / 提示词组装各一百多行，**逐行可讲**；这本身就是「知道什么时候不该上框架」的加分点。
4. **复用现有模型通道**：embedding 走 OpenAI 兼容 `/v1/embeddings`，可直接复用 `AppProperties.llm.Provider` 的结构（可配 SiliconFlow `BAAI/bge-m3` 或阿里百炼 `text-embedding-v4`）。
5. **自带降级叙事**：embedding 未配置/超时/报错 → **静默回落**原链路，`RAG_ENABLED=false` 时行为与当前**完全一致**。
6. **演进路径清晰**：数据量上万 或 需要更强召回 → 换 pgvector / Milvus，接口不变，只换 `VectorStore` 实现。

### 5.2 检索对象与隔离

- **检索单元**：历史 = **用户需求描述**（不是代码，代码只作注入内容）；模板 = 标题 + 图型 + 关键词。
- **数据隔离**：历史严格 `user_id = ?`；模板库全局（`source_type='template' AND user_id IS NULL`）。
- **切分策略**：不做字节硬切（代码切块无召回意义），以「一次生成」为单元。

### 5.3 落地设计（Java）

**新增表**（迁移 SQL，schema 变更集中在批次 1）：

```sql
CREATE TABLE IF NOT EXISTS rag_vector (
  vector_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_type ENUM('history','template') NOT NULL,
  user_id     INT NULL,             -- history 归属用户；template 为 NULL
  ref_id      INT NULL,             -- history → generation_history.history_id
  title       VARCHAR(255),
  embed_text  MEDIUMTEXT NOT NULL,  -- 参与向量化的文本
  content     MEDIUMTEXT NOT NULL,  -- 注入提示词的片段
  embedding   LONGTEXT NOT NULL,    -- JSON 数组文本
  dim         INT NOT NULL,         -- 维度一致性校验
  model       VARCHAR(64) NOT NULL, -- 换模型后可识别需重建
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_ref (source_type, ref_id),
  INDEX idx_user (user_id),
  INDEX idx_type (source_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**新增包** `service/rag/`：

| 类 | 职责 |
|---|---|
| `EmbeddingClient` | 封装 `/v1/embeddings`：超时、维度/模型校验、失败抛受控异常 |
| `VectorStore` | `rag_vector` 读写：参数化 Wrapper、按 `user_id` 隔离、按 `(source_type, ref_id)` 幂等 upsert |
| `Retriever` | 全量加载 → 余弦相似度 → 阈值过滤 → Top-k 排序 |
| `PromptComposer` | 组装 few-shot 段落，显式声明「仅参考图型写法，禁止照搬数据」，并说明与【上下文独立指令】不冲突 |
| `RagService`（门面） | `retrieve(userId, query)` / `indexHistory(userId, historyId, desc, code)`，统一开关、超时与**静默降级** |

**接入点只有两处**（零破坏原则）：

1. `ChatService.buildSystemPrompt` 返回前 → `ragService.retrieve(...)` 注入 few-shot；
2. `ChatService.saveGenerationHistory` 拿到 `historyId` 后 → **异步** `ragService.indexHistory(...)`。

**配置**（`AppProperties` 新增 `rag` + `embedding` 节点）：

```
app.rag.enabled / top-k-history(3) / top-k-template(2) / min-score / timeout-ms(3000)
app.embedding.api-key / api-url / model / dim
```

### 5.4 配套脚本

- `spring-backend/scripts/seed_templates`：抽取进阶图型模板（柱状/折线/饼/散点/堆叠柱/密集柱缩写/误差棒）→ 向量化 → 入库；
- `spring-backend/scripts/backfill_history`：批量回填历史（分批 + 并发上限 + 进度打印）；
- `spring-backend/scripts/rag_demo`：输入自然语言查询，打印 Top-k 与相似度得分（**面试演示用**）。

### 5.5 验收

- 抓 prompt 日志确认 few-shot 已注入；召回为空时确认回落原 prompt；
- `RAG_ENABLED=false` 与改造前行为一致；embedding 报错时主流程不中断（只记 `system_log`）；
- 用 E1 离线评估集对比「开/关 RAG」的**首轮通过率**（这就是路线图要的「前后对比数据」）。

---

## 6. Prompt 工程化 + 结构化输出（A2 / A3）

- **A2 模板版本化**：`PromptTemplates` 增加 `VERSION` 常量，注入时写入 `api_log` / `system_log`，便于「同一用例换版本对比」——面试可讲提示词版本管理。
- **A3 结构化输出**：
  - 提示词要求模型输出 JSON `{ "chart_type": ..., "code": ..., "summary": ... }`；
  - 解析顺序：**JSON 解析 → 失败再走现有 `ChartCodeExtractor` 正则兜底**（保持向后兼容，绝不因模型不听话而失败）；
  - 校验失败时**带错误信息重试一次**（复用 `generateWithFallback` 的重试位点）。
- 面试点：约束解码 / 结构化输出的工程约束 / 兜底与降级。

---

## 7. 编译任务队列化 + 并发控制 + 耗时拆解（G1 / G2 / O1）

> 现状：`POST /api/compile/{id}` 同步跑 XeLaTeX，最长阻塞 30s（`LatexCompiler` 超时）。

- **G1 轻量版（推荐先做，零新表）**：
  - 新增 `service/CompileTaskService`：`ConcurrentHashMap<taskId, Task>` + `ThreadPoolTaskExecutor`；
  - `POST /api/compile/{id}` 立即返回 `{task_id, status: "queued"}`；新增 `GET /api/compile/task/{taskId}` 查询状态；
  - 前端把「同步等待」改为**轮询**（后续可升级 SSE）。
  - **注意**：这是**对外契约变更**，需前端同步改（`ChartGenerator.vue` / `MyHistory.vue`）。
- **G1 演进版**：`compile_task` 表（状态机 `queued/running/success/failed`）+ 启动时恢复未完成任务，重启不丢。
- **G2 并发控制**：`app.latex.max-concurrency`（`Semaphore` 限流），避免 XeLaTeX 打爆机器。
- **O1 耗时拆解**：在 `ChatService`（LLM 耗时）与 `CompileService`（编译耗时）分段计时。
  - 推荐给 `api_log` 加 `duration_ms` 列（一条迁移），由 `AdminLogService` 聚合出均值/P95；
  - 不推荐塞进 `system_log` 文本（难聚合）。

---

## 8. 评估体系（E1 / E2 / E3）

- **E1 离线评估集**：`spring-backend/eval/cases.json`（固定一批 prompt）+ `eval.mjs`（复用 `verify.js` 风格，零依赖）。
  - 指标：生成成功率、代码可编译率、**首轮通过率**、平均耗时；
  - 每次改提示词/RAG 后跑一遍，产出**可写入简历的数字**。
- **E1 执行结果记录（2026-09-13，Trae 执行）**：`eval/results/rag-off.json` vs `rag-on.json`，各 10 用例；生成成功率 / 可编译率 / 首轮通过率均 1.0（基线饱和，当前用例区分不出 RAG 增量）；avg 延迟 15.4s → 16.8s（+9%，embedding + few-shot 的量化成本）。结论与叙事口径见 §12。
- **评估集 V2（批次 3 前置建议）**：当前 10 条偏常规、天花板已到。V2 方向：①进阶图型组合题（多系列误差棒、双 y 轴、密集标注+图例外置）；②含歧义/缺数据用例（考察 RAG 对图型选择的引导）；③新增静态质量维度——用脚本检查生成代码是否命中 R1-R8 反例特征（如 `legend pos=north west`、全角逗号、`{abs=...}`），把「通过」细化为「通过且零违例」，才能量出 RAG/Prompt 迭代的真实增量。
- **E2 失败归类**：在 `ChatService` 失败分支把原因分类落库（空回复 / 围栏不闭合 / 编译失败 / 上游错误），`AdminLogService` 出分类计数。
- **E3 质量看板**：复用 `AdminLog.vue`（已有 ECharts）加「通过率 + 耗时分位数」图表。
- 面试点：有评估意识、可量化迭代，而不是「跑通就行」。

---

## 9. 成本与健壮性（A4 / G3 / G4 / O2 / O4）

| 项 | 落点 | 要点 |
|---|---|---|
| A4 大数据集截断 | `util/FileContentReader` | 增加「列名 + 类型 + 前 N 行采样 + 行数」摘要模式（阈值可配），避免 100MB 全量进 prompt |
| G3 生成缓存 | `ChatService` 入口 | 缓存键 = **完全相同**的 prompt + `data_ids`（注意【上下文独立指令】语义，只缓存完全一致请求） |
| G4 限流 | `ChatService` / 过滤器 | `/api/chat` 按用户令牌桶（本机已有 Redis，`D:\Redis`） |
| O2 上传校验 | `DatasetService.upload` | 扩展名白名单 + 魔数校验（现在只限 100MB） |
| O4 启动配置校验 | `AppProperties` + `@PostConstruct` | `JWT_SECRET` 已 fail-fast；补齐 DB / LLM / embedding 相关必填校验，缺失时启动失败并给出明确提示 |

---

## 10. 不建议做的方向（沿用 t 文档，已按现状校准）

| 方向 | 为什么不做 |
|---|---|
| 在 MySQL 里硬塞向量索引 / ANN | 数据量小无收益，实现别扭；真正演进应换 pgvector / 专用向量库 |
| 双 / 多向量库并行检索 | 杀鸡用牛刀，面试容易被问崩 |
| 把检索铺到所有接口 | 只做 AI 生成链路，认证/编译/后台不动 |
| 为 RAG 硬造知识库内容 | 知识库天然只有「模板库 + 历史成功案例」两个来源，够了 |
| 为 demo 强引 LangChain4j / Spring AI | 除非专门要讲框架，否则自研更可控、更好讲 |
| 全量重写为 Java | **已完成**，不再是待办 |

---

## 11. 验收与回归

- **基线不回归**：每批次结束跑 `spring-backend/verify.cmd`（当前 PASS=27 / FAIL=0）。
- **RAG**：prompt 日志可见 few-shot；召回为空时回落原 prompt；`RAG_ENABLED=false` 行为不变。
- **结构化输出**：构造「模型返回散文」的用例，确认能回退正则兜底并成功入库。
- **编译队列**：`POST /api/compile/{id}` 的 P95 响应 < 100ms（不再阻塞 30s），任务最终状态可查询。
- **评估**：E1 输出首轮通过率，并在写入简历前保留「开 RAG 前 / 后」两组数据。

---

## 12. 面试叙事（把这份路线图讲成 3 分钟）

> 「这个项目生成图表代码不稳定。我先做了两件事：**把检索增强做进生成链路**——把历史成功案例和模板库向量化，生成前召回 Top-k 做 few-shot；以及**用结构化输出约束模型**，解析失败再兜底正则。
> 检索我没上向量库，因为数据量只有千级、单次暴力余弦是毫秒级，**盲目上 pgvector 是过度设计**；我把 embedding 不可用时做成了静默降级，主流程永不中断。
> 工程上我把同步阻塞 30s 的编译改成了**异步任务 + 状态查询**，并给 XeLaTeX 加了并发上限。
> 效果我用固定评估集量了：10 条用例跑「开/关 RAG」双轮对比，**两轮首轮通过率都是 100%——基线已经饱和**，因为提示词的渲染规则和正反例打磨得很充分，常规用例区分不出差异。但评估管线本身给出了两个有价值的数据：一是 RAG 开启的平均延迟 +9%（15.4s → 16.8s，embedding 调用 + few-shot 加长 prompt 的量化成本），二是它把「什么时候 RAG 有用」变成了可验证的问题——**它的价值在进阶图型和长尾写法上，下一步是把评估集升级到有区分度的版本再量**。
> 等数据量上万或召回不够，我再把 `VectorStore` 换成 pgvector——**接口不变，只换实现**。」

> **E1 执行结果（2026-09-13，Trae 执行）**：`eval/results/rag-off.json` 与 `rag-on.json`，各 10 用例；first_pass_rate 1.0 / 1.0（基线饱和），avg 15387ms / 16794ms（+9%），p95 40232ms / 41664ms。**追问预案**：「RAG 没提升通过率为什么还做？」——评估管线先建立才谈得上迭代；基线饱和本身是数据结论；RAG 的差异化价值需要更有区分度的用例集（评估集 V2），而不是先验断言。

> **E1 实测回填（2026-09-13）**：`eval/cases.json` 固定 10 条「自行生成」用例，`RAG_ENABLED=false → --tag=rag-off` 与 `RAG_ENABLED=true → --tag=rag-on` 各跑一轮，首轮通过率均为 **1.0（10/10）**。说明这批精选简单用例在基线已达天花板，未测出 RAG 增量；rag-on 全程有 `[RAG] 召回 N 条` 日志、异步索引正常。若面试需要「有差异的 X→Y」，需把 cases 换成更难/更模糊的用例（如含歧义、多数据源、边界数值）再对比，当前如实记为 1.0→1.0。

---

## 附：与旧文档的对应关系速查

| 本文件 | 来源 |
|---|---|
| A1 / §5 | `c优化方向评估.md` 全部 + `t优化方案清单.md` §3 + `w优化方向清单.md` AI-RAG |
| A2 / A3 / §6 | `t优化方案清单.md` §4.1 + `w优化方向清单.md` AI-Prompt 工程化 |
| A4 | `t优化方案清单.md` §4.2 |
| A5 / A6 | `w优化方向清单.md` AI 线 |
| E1 / E2 / E3 / §8 | `w优化方向清单.md` 评估体系 + `t优化方案清单.md` §4.4 |
| G1 / G2 / §7 | `w优化方向清单.md` 工程线 + `t优化方案清单.md` §4.3 |
| G3 / G4 | `t优化方案清单.md` §4.3 + `w优化方向清单.md` 缓存与限流 |
| G5 / G6 / O1–O4 | `w优化方向清单.md` 工程线与可观测安全线 |
| §10 | `t优化方案清单.md` §6 |
| §11 | `t优化方案清单.md` §7 + 本仓 `verify.cmd` |
