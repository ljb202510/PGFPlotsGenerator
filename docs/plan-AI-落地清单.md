# plan-AI 落地清单（19 项逐项对照 + 新增物速查）

> **用途**：对照 `docs/process/plan-AI.md`（原 19 项优化路线图），一次看清 **哪些真做了、具体做了什么、面试能讲什么、有什么可验证证据**。
> **口径**：截至 2026-09-14，结论一律以 `hello/` 下**当前代码与实跑输出**为准，不采用过程文档里的计划性描述。
> **核对依据**：`spring-backend/src/**`、`migrations/**`、`spring-backend/eval/results/*.json`、`spring-backend/verify.js`、`docs/log.md`。
> **怎么用**：§1 逐项看「具体做了什么 + 面试可以讲什么」；赶时间只读 §0 总览 + §3 数字表 + §7 面试口径。

---

## 0. 一屏总览

| 状态 | 数量 | 编号 |
|---|---|---|
| ✅ 完整落地 | 9 | A1、A2、A3、E1、E2、E3、G1（轻量版）、G2、O1 |
| ◐ 部分落地 | 1 | A5（只做了降级链，未做「按复杂度/长度路由」） |
| ✗ 未做 | 9 | A4、A6、G3、G4、G5、G6、O2、O3、O4 |
| ➕ 计划外新增 | 8 | 见 §4（路线图没写、但确实做了） |

**一句话结论**：路线图里「AI 亮点（批次1）+ 工程硬功（批次2）+ 评估与质量（批次3）」三批**全部落地并通过实测验收**；「成本与健壮性（批次4）」「体验与进阶（批次5）」**一项未做**；此外在落地过程中**自发多做了 8 项质量修复与增强**。

---

## 1. 逐项对照（每项：具体做了什么 / 面试可以讲什么 / 证据）

### ✅ A1 RAG 检索增强（P1，计划：MySQL 存向量 + Java 暴力余弦）

**具体做了什么**

1. 建 `rag_vector` 表：`embed_text`（参与向量化的文本）与 `content`（注入提示词的片段）**分开存**；带 `dim` / `model` 字段便于换模型时识别重建；唯一键 `(source_type, ref_id)` 保证 upsert 幂等。分两个区：`template`（`user_id IS NULL`，全局模板库）与 `history`（按 `user_id` 严格隔离）。
2. `EmbeddingClient` 走 OpenAI 兼容 `/embeddings`（`BAAI/bge-m3`，1024 维），做超时、维度/模型校验，失败抛受控异常。
3. `VectorStore` 全量加载 → `Retriever` **暴力余弦** → `min-score`(0.55) 阈值过滤 → Top-k 排序（历史 3 条 / 模板 2 条）。
4. `PromptComposer` 组装 few-shot 段落，显式声明「**仅参考图型写法，禁止照搬数据**」，并说明与【上下文独立指令】不冲突。
5. `RagService` 作门面统一开关、超时与**静默降级**：未配置 key / 超时 3s / 报错 → 返回 `""`，拼接结果与改造前**逐字符一致**（零破坏）。
6. **只改两处接入点**：`ChatService.buildSystemPrompt` 返回前注入；落库拿到 `historyId` 后**异步**索引（不拖慢用户响应）。
7. 运维 CLI 四件套：`rag_seed`（模板库）/ `rag_demo`（带 query 看召回，演示用）/ `rag_backfill`（历史回填）/ `rag_purge`（清理污染向量）。
8. **批次3.7 增强**：`Retriever` 同题断链（提问文字相同即剔除 + 相似度 ≥ `0.98` 即剔除，**模板库不受影响**）；`ChartCodeValidator.hasDuplicateSeries` 作为**入库准入**；`rag_purge` 实际清掉 9 条污染向量。

**面试可以讲什么**

- **「为什么不上 pgvector / LangChain4j」**（高频追问）：数据量千级、单次 `O(N×D)` 暴力余弦是毫秒级，零新中间件、部署成本为 0；框架黑盒被追问底层容易穿帮；演进路径清晰 —— 数据上万时**接口不变，只换 `VectorStore` 实现**。这本身是「知道什么时候不该上框架」的加分点。
- **「RAG 失败会不会拖垮主流程」**：不会。开关关闭时与旧版逐字符一致，embedding 报错只记 `system_log` 并回落原 prompt —— 增强项不能变成新的失败点。
- **最有价值的一次发现（强烈建议讲）**：**RAG 自污染循环**。历史入库存的是**完整代码**，同题查询相似度 ≈ 1.0 必然排第一 → 模型直接照抄上一次结果（**连同上次的错误**），形成自我强化。硬证据：5 条同题记录逐次复制、qwen 与 siliconflow 两种模型输出**逐字节相同**。解法是三层：同题断链（两道闸门）+ 入库准入 + 清理已污染数据。
- **阈值是量出来的，不是拍的**：同题相似度 **1.0000**、异题（直方图 vs 折线图）**0.6263**，取 0.98 既不误伤其它图型又能拦住照抄。
- **主动说局限**：断链只保证「不再照抄上一次那一份」，**不等于拿到正确范例**；真正保证直方图画成柱状图的仍是提示词规则 R10 + 编译前补 `ybar`。

**证据**：seed 7 条模板幂等；历史 backfill **181 条 0 失败**；召回 0.71 vs 无关 0.43；活体生成 few-shot 注入 + `chart_type` 落库。
**落点**：`migrations/create_rag_vector.sql`；`service/rag/{EmbeddingClient,VectorStore,Retriever,PromptComposer,RagService,RagUnavailableException}.java`；`tools/{RagCli,RagTemplates}.java`；`scripts/rag_{seed,demo,backfill,purge}.cmd`；`application.yml` 的 `app.rag` / `app.embedding`。

---

### ✅ A2 Prompt 工程化（P1）

**具体做了什么**

1. `PromptTemplates.VERSION = "v1.4-pie-no-axis"`，每次调用写入 `api_log.prompt_version`（新增列）→ **同一用例可换版本横向对比**。
2. 规则体系从 R1–R8 扩到 **R1–R12**：R9 多系列数据必须互不相同、R10 直方图必须 `ybar`、R11 `ymax` 必须留约 10% 余量、R12 文本里的 `%` 必须写 `\%`；每条都给出**唯一正确写法 + 反例**；自检清单补至 16 条、反例 +7。
3. **饼图专项规则**：禁止把 `\pie` 塞进 `axis`（会多画一个空坐标系）、整图只画一次（禁止「先画一版 → 注释说要重画 → 再画一版」的返工式输出）、标签与图例只能选一种、禁止自述性注释。
4. `NO_DATASET_SUFFIX` 改**双分支**：需求模糊（只说「折线图」）→ 可自拟示意数据；指明主题（「近五年 GDP」）→ 必须使用已知权威数据并注明年份与来源。实测生效：模型开始主动标注「数据来源：示意数据（无真实来源）」。
5. 模型名换为在架的 `THUDM/GLM-4-9B-0414`（原 `glm-4-9b-chat` 被平台下架，调用返回 `30003 Model disabled`）。

**面试可以讲什么**

- **「提示词不是玄学，是版本化资产」**：`VERSION` + 落库 `prompt_version`，让「换提示词是否真变好」变成可查的问题；评估集负责给结论。
- **「规则不是我编的，是失败驱动的」**：每条规则都对应一个真实事故 —— `color={blue, red, green}` 被 xcolor 当单个颜色名刷屏 50+ 条错误并把编译拖到 30s 超时；`symbolic x coords` 里用全角逗号导致所有柱子挤中间；`ymax=40000` 而数据 40184 柱子被裁；文本里的裸 `%` 吞掉整行导致括号失衡。可讲「先有失败样本，才有规则」。
- **纵深防御的叙事**：提示词里「禁止」的事，服务端 `preprocess` 还会再做一层**确定性修复**；不假设「提示词写了模型就一定照做」。

**落点**：`util/PromptTemplates.java`、`entity/ApiLog.promptVersion`、`migrations/alter_api_log_prompt_version.sql`、`ChatService`（426/499 行）。

---

### ✅ A3 结构化输出（P1）

**具体做了什么**

1. 提示词追加【结构化输出约定】：要求先输出 JSON `{chart_type, code, summary}`，再给 ```latex 围栏。
2. **三级解析顺序**：`StructuredOutputParser`（Jackson 解 JSON）→ 前端显式传入的 `chart_code` → `ChartCodeExtractor` 围栏块/裸代码正则兜底。**绝不因模型不听话而失败**。
3. **校验失败带错误信息重试一次**：若「JSON 无效且正则也提不出代码」，拼一段「【上次输出无效】…请严格按结构化输出约定重新输出」的同模型纠正请求。
4. 响应在结构化可用时附加 `chart_type` / `summary`（为 `null` 时不放入，**保持旧客户端兼容**）。
5. 批次3.6 追加 `ChartCodeExtractor.normalizeEscapes`：把模型输出的**字面 `\n`**（反斜杠+n 两个字符）还原为真实换行 —— 这是 `bar_dense` 编译失败的根因。

**面试可以讲什么**

- **「结构化输出是工程约束，不是许愿」**：三级兜底意味着任何一级能出结果就不会失败；提示词只是「提高概率」，兜底才是「保证可用」。
- **修一个 bug 先想它会误伤什么**：字面 `\n` 不能朴素替换 —— `\node`、`\newline`、`\text`、`\times`、`\tiny` 都以 `\n` / `\t` 开头。实现用「后面不跟 ASCII 字母」的 lookahead + 「前面不是反斜杠」的 lookbehind 双重保护，并写了 `ChartCodeExtractorTest` 7 例（含误伤反例）。**这就是「修复的副作用意识」**。
- **有前后对比数据**：该修复让 `bar_dense` 从编译失败转为通过，首轮通过率 **0.938 → 1.0**。

**落点**：`util/StructuredOutputParser.java`、`util/ChartCodeExtractor.java`、`ChatService`（141–178 行、`attemptOn` 302–324 行）。

---

### ✅ E1 离线评估集（P1，并升级到 V2 + 静态质量维度）

**具体做了什么**

1. `eval/cases.json` **16 例**（原 10 条常规 + 6 条进阶/歧义/缺数据）；`eval.mjs` 零第三方依赖，风格与 `verify.js` 一致；`eval/violations.mjs` 做静态违例检测。
2. 指标定义：生成成功率 / 可编译率（占生成）/ **首轮通过率**（生成成功 && 代码非空 && 编译成功）/ **零违例通过率**（再要求 `detectViolations` 为空）/ avg & P95 chat 耗时 / `degraded_count`。
3. 每例记录 `model_used`，与请求通道不同即标注「降级」，并**在报告里明确提示这些不计入该通道自身能力**。
4. 结果文件按 `--tag=` 归档（8 个结果文件全部保留）；规则版本一变**必须重跑并换 tag**，避免「新规则 + 老数字」。
5. `violations.mjs` 当前静态覆盖 **R1–R3、R5–R9、R12**；R9 判定与 `ChartCodeValidator.hasDuplicateSeries` **逐条同源**。

**面试可以讲什么**

- **「先有评估管线，才谈得上迭代」**：基线饱和（1.0 → 1.0）也是**数据结论**，如实记录不粉饰 —— 这正是「E1 先于 A1/A2 落地」的理由。
- **「指标不能自我欺骗」（很能加分）**：R10 / R11 已被编译前预处理**确定性修复**，模型原始输出里的违例在编译阶段就消失了 → 静态检测它们会**恒为零违例**，只会让零违例率虚高、失去区分度，所以**刻意不检**，把口径交给 `preprocess`；R4（量纲一致）需要理解语义标度（万元 vs 元），正则会大量误报 → **保留人工评审**。这些取舍全部写进了代码注释。
- **「怎么让评估可信」**：口径与入库准入同源（评估说合规 = 入库不会被拒），避免「评估说 OK、入库被挡」的分裂；改规则必须重跑 + 换 tag + 保留旧文件 → 可复现、可追溯。
- **指标怎么用**：`zero_violation_rate` 是「把通过细化为通过且零违例」，否则常规用例一饱和就再也量不出提示词/RAG 的增量。

**证据**：`v3.0-qwen-rag-on`（16 例）生成成功率 / 可编译率 / 首轮通过率 / 零违例率 **均 1.0**，avg 6687ms、P95 10969ms、降级 0。
**落点**：`spring-backend/eval/{cases.json,eval.mjs,violations.mjs,results/*.json}`。

---

### ✅ E2 失败归类（P2）

**具体做了什么**

1. 定义 7 类枚举（`common/ErrorTypes.java`）：`UPSTREAM_API_ERROR`、`UPSTREAM_CONNECTION_ERROR`、`UPSTREAM_UNKNOWN_ERROR`、`EMPTY_REPLY`、`CODE_EXTRACT_FAIL`、`COMPILE_ERROR`、`COMPILE_QUEUE_FULL`。
2. **补齐两个既有缺口**：① 空回复此前只写 `system_log`、**没落 `api_log`**，导致失败归类整类缺失；② 「提取不到代码」此前被记成 success。
3. **编译类失败只在 `CompileTaskService.recordCompileFailure` 落库**（唯一落库点），避免一次失败被计两条。
4. `AdminMapper.xml` 聚合出分类计数，供管理端使用。

**面试可以讲什么**

- **「失败可观测」是从「能跑」到「能运维」的分界线**：只有知道失败**分布在哪儿**，才谈得上定向优化（这也直接支撑了 E3 看板）。
- **口径纯净的一个细节**：编译失败**不写 `prompt_version`** —— 编译发生在生成之后，此处并不知道当次生成用的版本，写「当前版本」会造成误读，所以留 `NULL`。可讲「宁可缺数据，不要有误导数据」。
- **怎么发现缺口的**：不是设计出来的，是「按分类去查库发现一条都没有」倒推出来的 —— 这类「统计口径 vs 实际写入」的对账意识很实用。

**落点**：`common/ErrorTypes.java`、`migrations/alter_api_log_error_type.sql`、`CompileTaskService`、`resources/mapper/AdminMapper.xml`。

---

### ✅ E3 质量看板（P2）

**具体做了什么**

1. 管理端 `AdminLog.vue` 新增**质量看板**：失败分类**饼图** + 按日 **avg / P95 双折线** + 平均耗时 / P95 卡片（带 `sample_count`）+ 近期失败列表。
2. **三条空态规则**：区间内无数据时显示占位文案（「暂无失败记录」「该区间暂无耗时数据」），**不画 0 值图**；耗时卡片无样本时显示「—」而不是「0 ms」。
3. 后端三条断言由 `verify.js` 固化：`responseTime`、`errorTypes`、`responseTimeSeries`。
4. 单日区间只标两个数值点、多日不加标签（避免密集标签互相遮挡）；图例由 `top:24` 移到右上角（原位置与居中标题垂直重叠，被标题压住造成「图例消失」的误判）。

**面试可以讲什么**

- **「空态也是功能」**：0 值图会误导判断（看起来像「当天全失败」或「耗时为 0」），空态 + 「—」才是诚实表达。
- **闭环叙事**：看板上看出问题 → 定位到具体分类/用例 → 改提示词或 RAG → 再跑评估集验证 → 数字回到看板。这是「数据驱动迭代」的完整故事。
- **排查方法论**：先怀疑代码、后放大 canvas 复现，才确认是**图例与标题重叠**而不是「图例没渲染」—— 可讲「不要凭现象下结论」。

**落点**：`src/views/AdminLog.vue`、`resources/mapper/AdminMapper.xml`、`verify.js` 三条断言。

---

### ✅ G1 编译任务队列化（P1，**轻量版**）

**具体做了什么**

1. 新增 `service/CompileTaskService`：**内存任务表**（`ConcurrentHashMap`，上限 500，超出淘汰最旧终态任务）+ 独立 `ThreadPoolTaskExecutor`。
2. 契约变更：`POST /api/compile/{history_id}` **立即返回** `{task_id, status:"queued"}`；新增 `GET /api/compile/task/{task_id}` 轮询终态（`queued / running / success / failed`，终态带 `pdf_path` / `file_size` / `duration_ms`）；前端 `ChartGenerator.vue` 同步改为轮询。
3. 队列满（`maxPoolSize(4) + queueCapacity(100)`）→ **返回 503 + 落库 `COMPILE_QUEUE_FULL`**，任务绝不静默丢弃。
4. `queueCapacity` 提为可注入（`COMPILE_QUEUE_CAPACITY`），使「队列满」可被自动化验收。

**面试可以讲什么**

- **契约变更怎么落地**：这是对外契约变更，前后端必须同步改；保留状态机语义（4 态）而不是只返回成功/失败。
- **一个真踩到的坑**：**不能用 `@Async`** —— 同类内自调用会让异步代理失效；这里直接向注入的线程池 `submit`。这类「Spring 代理机制」细节很能体现基础。
- **主动说边界**：内存任务表**重启会丢状态**，演进版是 `compile_task` 表持久化 + 启动恢复；轻量版已满足「不再阻塞 30s」，**明确说出取舍比藏起来好**。
- **前后对比**：4 任务并发提交响应 **14–19ms**，改造前同步阻塞最长 **30s** —— 这是最有冲击力的一个数字。

**落点**：`service/CompileTaskService.java`、`controller/CompileController`、`src/views/ChartGenerator.vue`、`config/AsyncConfig`。

---

### ✅ G2 编译并发控制（P2）

**具体做了什么**

- `app.latex.max-concurrency`（默认 2）→ 工作线程内用 `Semaphore` 限流 XeLaTeX **重进程**并发；压测（4 任务并发提交）证明同一时刻 XeLaTeX 并发 ≤ 2。

**面试可以讲什么**

- **「重进程不能无限并发」**：XeLaTeX 是 CPU/内存大户，不加限流会把机器打爆；限流位置放在**工作线程内**（限的是真实进程数），而不是提交处（提交处限的是排队数）。
- 与 G1 的组合叙事：**队列解决「不阻塞」，信号量解决「不压垮」** —— 一个管响应时间，一个管资源隔离。

---

### ✅ O1 链路耗时拆解（P2）

**具体做了什么**

1. `api_log` 新增 `duration_ms`（迁移 `alter_api_log_duration_ms.sql`），**成功与失败两条路径都落库**。
2. `api-stats` 返回真实 **avg / P95 / sample_count**，替换原硬编码 245 / 420。
3. 编译耗时记在**编译任务终态**，不混算进 `api_log`（生成链路口径保持纯净）。

**面试可以讲什么**

- **「耗时数据要能聚合」**：为什么加**列**而不是塞进 `system_log` 文本 —— 文本无法 SQL 聚合出 P95。
- **口径纯净**：生成耗时（LLM 段，含降级链全部重试时间）与编译耗时分开统计，避免「一个接口两种耗时混在一列」导致看板失真。
- **P95 而不是平均值**：平均值会被快请求拉平，P95 才暴露长尾 —— 这是「优化长尾」的前提。

**落点**：`entity/ApiLog.durationMs`、`migrations/alter_api_log_duration_ms.sql`、`service/AdminLogService` + `AdminMapper.xml`。

---

### ◐ A5 多模型路由（P2，**部分落地**）

**具体做了什么（已做部分）**

1. 三通道 `qwen → siliconflow → deepseek` 固定优先级**降级链**，主通道失败后**从其之后**逐个尝试、到链尾即止（**不回绕**）。
2. **只对「换通道可能成功」的错误接力**：`402 / 408 / 429 / 5xx`，以及消息含 `insufficient balance` / `quota` / `rate limit` 的 4xx；`400 / 401 / 403` 等确定性错误**直接报错不重试**。
3. `enabled=false` 或未配置 key 的层**整层跳过**（绝不用空 key 发请求，否则会被上游回 401 而误记成「上游鉴权失败」）。
4. `model_used` 写入响应与历史 `metadata`，**降级不静默**；降级成功打 `log.warn` 并写系统日志（不记 key）。
5. `verify.js` 用内置 Node `http` stub（零密钥、零外网）断言三条路径：可降级 500 接力成功 / 不可降级 401 不重试且备用通道调用 0 次 / 不传 `model` 回落主通道。

**未做部分**：按「复杂度 / 长度」选择模型的路由策略。

**面试可以讲什么**

- **「为什么不能一律重试」**：把 45s 超时叠成 90s，用户体感更差；只有「换通道可能成功」的错误才值得接力。
- **降级链修复的故事性很强**：旧实现只在「主通道返回内容为空」时才切 backup，**硬错误（余额不足/限流/5xx）直接冒泡**成用户可见 5xx —— 这正是上一轮 **16 连败**「结构性不会自愈」的根因。
- **stub 验证的工程价值**：用本地 stub 造 500/401 验证**链路语义**，零密钥零外网、可进 CI；同时**诚实说明**「验的是语义，不是真实厂商故障」。
- **测试抓出真回归**：`resolve` 里 `List.of(...).contains(null)` 会抛 NPE（旧实现 `"qwen".equals(model)` 对 null 安全），导致**不传 `model` 的请求直接 500** —— 被新增用例抓出并固化。

---

### ✗ 未落地的 9 项

| 编号 | 计划内容 | 状态 |
|---|---|---|
| A4 | 大数据集截断（列名+类型+前 N 行采样） | 未做（批次4） |
| A6 | 流式输出 SSE | 未做（批次5） |
| G3 | 生成缓存（相同 prompt + data_ids 命中复用） | 未做（批次4） |
| G4 | `/api/chat` 按用户限流 | 未做（批次4） |
| G5 | 前端统一 request 封装层 | 未做（批次5） |
| G6 | 14 条路由全局守卫 | 未做（批次5） |
| O2 | 上传扩展名 + 魔数校验（现仅 100MB） | 未做（批次4） |
| O3 | 编译沙箱 / 进程隔离 | 未做（批次5） |
| O4 | 启动配置校验补齐（现仅 `JWT_SECRET` fail-fast） | 未做（批次4） |

> 这 9 项**不是漏了，是按性价比排期砍掉的**；每项在 §5 都有一句「为什么不做的口径」，面试被追问时可直接答。

---

## 2. 新增物速查（按类型）

### 2.1 数据库

| 变更 | 内容 |
|---|---|
| 新增表 ×1 | `rag_vector`（`source_type/user_id/ref_id/title/embed_text/content/embedding/dim/model` + `uk_source_ref` 幂等键 + `idx_user`/`idx_type`） |
| 新增列 ×3 | `api_log.duration_ms`（O1）、`api_log.error_type`（E2）、`api_log.prompt_version`（A2） |
| 表总数 | 11 → **12**（⚠️ 见 §6 事实偏差） |

### 2.2 新增配置项

| 配置 | 默认 | 归属 |
|---|---|---|
| `RAG_ENABLED` / `app.rag.enabled` | `false` | A1 总开关 |
| `top-k-history` / `top-k-template` | 3 / 2 | A1 召回条数 |
| `RAG_MIN_SCORE` | 0.55 | A1 召回下限 |
| `RAG_MAX_HISTORY_SCORE` | 0.98 | 批次3.7 同题断链上限 |
| `RAG_TIMEOUT_MS` | 3000 | A1 超时即降级 |
| `history-limit` / `template-limit` | 500 / 200 | A1 加载上限 |
| `EMBEDDING_API_KEY/URL/MODEL/DIM` | — / siliconflow / `BAAI/bge-m3` / 1024 | A1 向量化（与生成通道 key 分开） |
| `LATEX_MAX_CONCURRENCY` | 2 | G2 |
| `COMPILE_QUEUE_CAPACITY` | 100 | G1（批次3.6 起可注入，供队列满验收） |
| `QWEN_ENABLED` / `SILICONFLOW_ENABLED` / `DEEPSEEK_ENABLED` | true | 批次3.5 通道开关 |

### 2.3 新增 Java 类

| 包 | 类 |
|---|---|
| `service/rag/` | `EmbeddingClient`、`VectorStore`、`Retriever`、`PromptComposer`、`RagService`、`RagUnavailableException` |
| `service/` | `CompileTaskService`（G1+G2） |
| `util/` | `StructuredOutputParser`（A3）、`ChartCodeValidator`（批次3.7 入库准入） |
| `common/` | `ErrorTypes`（E2） |
| `tools/` | `RagCli`（seed/demo/backfill/purge）、`RagTemplates` |

### 2.4 新增脚本（均在 `spring-backend/scripts/`）

`rag_seed.cmd`（模板库）、`rag_demo.cmd`（带 query 看召回，面试演示用）、`rag_backfill.cmd`（历史回填）、`rag_purge.cmd`（清理污染向量）；
原有 `build / run / mvn-run / verify` 也统一归档进 `scripts/`。

### 2.5 API 契约变更（前后端已同步）

| 接口 | 变化 |
|---|---|
| `POST /api/compile/{history_id}` | 同步阻塞 → **立即返回** `{task_id, status:"queued"}` |
| `GET /api/compile/task/{task_id}` | **新增**，轮询终态（含 `pdf_path` / `file_size` / `duration_ms`） |
| `POST /api/chat` | 响应新增 `model_used`（批次3.5）；结构化可用时附加 `chart_type` / `summary`（A3） |

### 2.6 前端

- `AdminLog.vue`：E3 质量看板（失败分类饼图 + 按日 avg/P95 双折线 + 耗时卡片 + 三条空态）；批次3.6 修掉三个 `ResizeObserver` **无法释放**的缺陷（此前是函数内局部变量，函数返回即失引用；现新增登记表 + `onUnmounted` 中**先 disconnect 再 dispose**）。
- `ChartGenerator.vue`：编译改**轮询**；模型切换改为三项下拉；`displayText` 剔除 ``` 围栏块（含**未闭合的尾部围栏**，防 `max_tokens` 截断把 JSON 残块留在界面）。

### 2.7 文档

`hello/README.md` 新增「§5 AI 生成链路」并补实测数据；`spring-backend/README.md` 补 RAG 分区与配置、包结构、验证基线；`docs/log.md` 分批落地记录；批次执行方案归档至 `docs/process/`。

---

## 3. 可验证的数字（可直接用于简历/面试）

| 指标 | 数值 | 来源 |
|---|---|---|
| 编译提交响应（4 任务并发） | **14–19ms**（改造前同步阻塞最长 30s） | `docs/log.md` 批次2 |
| XeLaTeX 并发上限 | **≤ 2**（`Semaphore`，压测证明） | 批次2 |
| 离线评估集（16 例，v3.0-qwen-rag-on） | 生成成功率 / 可编译率 / 首轮通过率 / 零违例率 = **1.0 / 1.0 / 1.0 / 1.0**；avg 6687ms、P95 10969ms、降级 0 | `eval/results/v3.0-qwen-rag-on.json` |
| 接口回归 | **PASS=42 FAIL=0 WARN=0** | `scripts/verify.cmd` |
| 单元测试 | **39 例全绿**（LatexCompiler 14 / ChartCodeValidator 6 / Retriever 7 / ChartCodeExtractor 7 / StructuredOutputParser 5） | `mvn test` |
| RAG 效果 | 模板 seed 7 条幂等；历史 backfill 181 条 0 失败；召回 0.71 vs 无关 0.43；同题相似度 1.0000 vs 异题 0.6263 | `docs/log.md` 批次1 / 批次3.7 |
| 编译前预处理 | `hist351` 样本 xelatex 报错数 **100 → 0** | 批次3.7 |
| 评估基线演进 | 首轮通过率 0.938（有字面 `\n` 缺陷）→ **1.0**（批次3.6 修复后） | `v2-qwen-rag-on` → `v2.1-qwen-rag-on` |

---

## 4. 计划外新增（路线图里没有，但确实做了）

| # | 内容 | 属于 | 一句话价值 |
|---|---|---|---|
| 1 | 三通道 + 降级链修复（硬错误按固定优先级接力） | 批次3.5 | 修掉「16 连败」的根因：硬错误不再直接冒泡 |
| 2 | RAG 同题断链 + 入库准入 + `rag_purge` | 批次3.7 | 切断自污染循环，错误案例不再被当范例喂回 |
| 3 | 编译前五步确定性预处理 | 批次3.7 | 模型写错也能出对图（空 axis / 补 ybar / ymax 覆盖 / 非法 color / at 补括号） |
| 4 | 字面 `\n` 转义还原 | 批次3.6 | `bar_dense` 由失败转通过，首轮通过率回 1.0 |
| 5 | `queue-capacity` 可注入 +「队列满 → 503 + 落库」验收 | 批次3.6 | 把「任务不静默丢弃」从口号变成硬证据 |
| 6 | 文档外壳 xcolor 选项位置修复 | 本次打磨 | 命名色（`SteelBlue` 等）从「静默失败」恢复为正常渲染 |
| 7 | `eval/violations.mjs` 补 R9/R12 | 本次打磨 | 评估口径与提示词、入库准入三者对齐 |
| 8 | `AdminLog.vue` 图表生命周期修复 | 批次3.6 | SPA 往返 5 轮 0 errors，观察者可释放 |

> **第 6 项是最能体现「较真」的一处**：`\usepackage{xcolor}[dvipsnames,svgnames]` 写法把选项写在 `{}` 之后，LaTeX 会把 `[dvipsnames,svgnames]` 当正文排版 → 命名色**从未加载**，但**编译不报错**（只在日志里留 `Missing character ... in font nullfont!`），是典型的「静默失败」。修法是唯一安全的一种：在 `\usepackage{pgfplots}` **之前** `\PassOptionsToPackage{dvipsnames,svgnames}{xcolor}`（因为 xcolor 已被 tikz/pgfplots 提前加载，直接写 `\usepackage[...]{xcolor}` 会触发 `Option clash`）。

---

## 5. 未落地的原因（便于回答「为什么没做」）

| 未做项 | 原因 |
|---|---|
| A4 / G3 / G4 / O2 / O4（批次4） | 属「小工作量、中价值」补强，用户明确本轮只打磨不补功能 |
| A6 / O3 / G5 / G6（批次5） | 低价值或大工作量，用户明确不做 |
| G1 演进版（`compile_task` 表 + 重启恢复） | 轻量版已满足「不再阻塞 30s」，持久化属演进项，代码注释已标注取舍 |
| A5「按复杂度路由」 | 只做了降级链；按复杂度选模型需要真实的成本/延迟对比数据，性价比低 |
| R4 静态检测 | 需要理解数据语义标度（万元 vs 元），正则会大量误报，保留人工评审 |
| R10 / R11 静态检测 | 已被编译前预处理**确定性修复**，静态检测恒为零违例，只会让指标虚高（评估不能自我欺骗） |

---

## 6. 顺手发现的 3 处事实偏差（建议后续修正）

| # | 位置 | 现状 | 应为 |
|---|---|---|---|
| 1 | `hello/README.md`、`spring-backend/README.md` 的「11 张表」 | 11 | **12**（新增 `rag_vector`；实体类也已有 12 个） |
| 2 | `docs/process/plan-AI.md` §2 / §4 / §11 | 基线写 `PASS=27` | 现基线 **PASS=42**（§4「27 项基线不回归」同） |
| 3 | `docs/process/plan-AI.md` §4 批次3「唯一未闭环：rag-off 因 DeepSeek 余额作废」 | 待充值重跑 | DeepSeek 已停用、统一改用 qwen，**该轮不再需要重跑**（`docs/log.md` 已记录） |

---

## 7. 面试口径（把这份清单讲成 30 秒）

> 「路线图我拆成三批做：**批次1 做 AI 亮点** —— RAG 检索增强（自研 MySQL 存向量 + 暴力余弦，不上向量库）+ 提示词工程化（R1–R12 规则、版本号落库）+ 结构化输出（JSON 优先、正则兜底、失败带错误重试）；**批次2 做工程硬功** —— 把同步阻塞 30s 的编译改成异步任务（并发提交响应 14–19ms），加 `Semaphore` 限流和耗时拆解；**批次3 做评估与质量** —— 离线评估集 16 例 + 静态违例检测 + 失败 7 类归类 + 管理端质量看板。
> 结果用固定评估集量：首轮通过率 1.0、零违例率 1.0，接口回归 PASS=42，单测 39 例全绿。
> 过程中最有价值的一次发现是 **RAG 自污染循环**：历史案例存完整代码，同题相似度 ≈ 1.0 必然排第一，模型于是照抄上次结果、连错误一起复制；我用「同题断链 + 入库准入 + 清理污染数据」三层切断它，并留下硬证据。
> 批次4/5（截断、缓存、限流、上传校验、启动校验等）我**明确没做** —— 不是漏了，是按性价比排期砍掉的。」
