<!-- 本地使用说明：后端 cd spring-backend && scripts\run.cmd（开发模式 scripts\mvn-run.cmd）；前端在 hello 根目录 npm run serve；数据库 mysql -u root -p 登录输入密码 000; use X; source 1.sql; 并执行下方迁移（见 §4 快速开始）。 -->
<!-- AI 接口配置在 data/.env（Java 后端启动时自动读取 ../data/.env）：NSCC_* → Qwen3.5（主通道）、SILICONFLOW_* → GLM-4.9（降级链第二层）、DEEPSEEK_* → DeepSeek（当前 DEEPSEEK_ENABLED=false 停用，余额为 0）；EMBEDDING_* → RAG 向量化（bge-m3，与生成通道分开计费）。 -->
<!-- 注意：原 Node/Express 后端已于 2026-09-13 退役删除并归档到仓库外；其数据目录 backend/ 已改名为 data/，现仅保留 .env、uploads/、storage/ 共享运行时数据（Java 后端仍在使用），请勿删除。 -->
<!-- 管理员账号预置：admin123 / 666666（见 1.sql 与 §9 版本说明）。admin@pgfplots.com user_id=1 -->
# PGFPlotsGenerator 智能图表生成系统

> 通过自然语言（可附带 Excel/CSV 数据集）生成 PGFPlots/TikZ 图表代码，再用 XeLaTeX 编译为 PDF 在线预览与下载的全栈应用。前端 Vue 3 + 后端 Spring Boot（`spring-backend/`）+ MySQL（原 Node/Express 后端已退役）。

- **普通用户**：对话式生成图表 → 编译 PDF → 历史回溯 / 数据集管理 / 多轮会话 / 反馈与通知
- **管理员**：用户管理、系统通知发布、反馈处理、系统日志与 API 统计
- **核心链路**：`自然语言(±数据集) → RAG 召回 few-shot → AI 生成 LaTeX → 结构化解析 → 编译前预处理 → XeLaTeX 异步编译 → PDF 预览/下载`

> **版本**：本文档 v3.7，最近更新 2026-09-14；实证数据来源为 `spring-backend/eval/results/` 与 `spring-backend/verify.js` 实际运行结果，结论以 `hello/` 下源码为准。

## 1. 文档导航

> v3.0 起 README 收敛为「总入口」，细节不再重复维护，按需进入对应专项文档：

| 你想了解 | 文档 |
|---|---|
| 系统架构图、核心模块与数据流、数据库 ER | `docs/architecture.md` |
| 生产部署全流程、`.env.example`、备份/日志/排障 | `docs/deployment.md` |
| 本地开发、编码规范、二次开发/新增接口 | `docs/development.md` |
| **完整 API 接口契约（OpenAPI 3.0）** | `docs/openapi.yaml` |
| 迭代与部署流水记录 | `docs/log.md` |
| AI 能力路线图（RAG / 结构化输出 / 评估集 / 质量改进） | `docs/plan-AI.md` |
| 各批次详细执行方案 | `docs/process/plan-AI-批次1｜2｜3-执行方案.md` |
| 后端细节（构建、配置、降级链、包结构、验证） | `spring-backend/README.md` |
| AI 编码代理协作规则 | `.github/copilot-instructions.md` |

## 2. 技术栈

| 层 | 选型（已核实） | 依据 |
|---|---|---|
| 前端 | Vue `^3.2.13` + Vue Router 4 + **Vuex 4**（非 Pinia）；Element Plus `^2.11.9` + `@element-plus/icons-vue`（无 Font Awesome）；ECharts `^6.0.0`（管理员监控）；axios（组件直调，**无 src/api 封装层**）；mitt 事件总线 | `package.json` |
| 构建 | **Vue CLI 5**（`vue-cli-service serve/build`，**非 Vite**） | `package.json`、`vue.config.js` |
| 后端 | **Spring Boot `3.2.12` + MyBatis-Plus `3.5.5`**（`spring-backend/`，Java 17）；HikariCP 连接池；Spring Security 无状态 JWT + BCrypt；响应统一 `{success,data,message}` | `spring-backend/README.md` |
| 后端（已退役） | 原 Node.js + Express 后端（13 路由）已于 2026-09-13 全量退役删除，Java 版接口路径与行为等价 | `docs/log.md` |
| 大模型调用 | Spring 6 `RestClient` 调 **Qwen3.5**（湖大超算 NSCC MaaS，主通道）→ **GLM-4.9**（硅基流动，降级第二层）→ **DeepSeek**（已停用）；三通道共用 OpenAI 兼容 `/chat/completions` | `client/LlmClient.java`、`service/ChatService.java` |
| RAG 检索增强 | 自实现向量检索：`EmbeddingClient`（OpenAI 兼容 `/embeddings`，`BAAI/bge-m3`，1024 维）+ `VectorStore`（`rag_vector` 表，暴力余弦，**不引入 pgvector / 向量库**）+ `Retriever` + `PromptComposer` | `service/rag/` |
| 输出解析 | JSON 结构化输出 → `StructuredOutputParser`（Jackson）→ `ChartCodeExtractor`（围栏块提取 + 字面 `\n` 转义还原）双层兜底 | `util/StructuredOutputParser.java` |
| 编译 | `ProcessBuilder` 调 **XeLaTeX**（30s 超时；中文字体 SimSun/Times New Roman）；编译前 `preprocess` 五步确定性修复；异步任务队列 + `Semaphore` 并发上限 | `util/LatexCompiler.java` |
| 质量保障 | 提示词规则 R1–R12 + `ChartCodeValidator`（RAG 入库准入）+ `eval/violations.mjs`（离线静态违例检测）+ 16 例离线评估集 | `util/PromptTemplates.java`、`spring-backend/eval/` |
| 邮件 | spring-boot-starter-mail（JavaMailSender，QQ SMTP 发送验证码） | `service/VerificationService.java` |
| 上传/解析 | Spring `MultipartFile`（≤100MB）+ Apache POI 5.2.5（xlsx） | `controller/DatasetController.java`、`util/FileContentReader.java` |
| 存储 | MySQL（库名 `X`，**11 张表**）+ 文件系统（`data/uploads/`、`data/storage/`，Java 默认沿用） | `1.sql` + `migrations/` |

> 注意：`data/` 目录（由原 Node 后端目录 `backend/` 改名而来）现为**共享运行时数据目录**，仅保留 `.env`（Java 启动依赖）、`uploads/`、`storage/`，请勿删除该目录。

## 3. 目录结构

```
hello/
├── README.md                # 本文档（总入口）
├── 1.sql                    # 建库 X + 8 张基础表 + 预置管理员
├── migrations/              # SQL 迁移：notice 定向字段、notice_read 已读表、conversations、api_log 扩展列
├── docs/                    # 专项文档：architecture / deployment / development / openapi.yaml / log / plan-AI
│   └── process/             # plan-AI 各批次执行方案（过程文档，只读存档）
├── public/  dist/           # 前端模板 / 构建产物
├── src/                     # 前端 Vue 3 SPA
│   ├── main.js              # 入口：ElementPlus/Vuex/router + 全局注册 ui/ 组件 + ResizeObserver 防抖补丁
│   ├── config.js            # API_BASE_URL（默认 http://localhost:3000）
│   ├── App.vue              # 布局外壳：未登录→TheAuth；用户/管理员分区渲染 router-view
│   ├── Admin.vue            # 管理员概览首页（路由 /admin）
│   ├── router/index.js      # 14 条平级路由（13 页面组件 + 1 redirect，懒加载，无全局守卫）
│   ├── store/index.js       # Vuex：currentUser / isAuthenticated / unreadCount
│   ├── styles/              # tokens.css（设计令牌）、theme.css
│   ├── components/          # TheAuth/Login/Register/AdminLogin/CommonNavbar/AdminNavbar/AdminSidebar + ui/（AppCard 等）
│   └── views/               # ChartGenerator、MyHistory、DataUpload、MyFeedback、MyNotice、ChangeInformation、AdminFeedback/AdminLog/AdminNotice/AdminUser
├── spring-backend/          # ★ 后端：Spring Boot + MyBatis-Plus（唯一后端，见 spring-backend/README.md）
│   ├── scripts/             # 一键脚本：build / run / mvn-run / verify / rag_seed / rag_demo / rag_backfill / rag_purge
│   ├── eval/                # 离线评估：cases.json（16 例）+ eval.mjs + violations.mjs + results/*.json
│   └── src/main/java/com/pg/pgfplots/service/rag/   # RAG：EmbeddingClient / VectorStore / Retriever / PromptComposer
└── data/                    # 共享运行时数据目录（原 backend/ 改名而来，请勿删除）
    ├── .env                 # 环境配置（Java 启动时自动读取；含 DB/SMTP/LLM/Embedding 密钥，勿提交 .env）
    ├── uploads/             # 数据集原文件（Java 默认 UPLOADS_DIR 指向此处）
    └── storage/             # history/{uid}/{id}.json、generated_charts/user{uid}/hist{id}.pdf
```

## 4. 快速开始（本地开发）

### 4.1 前置依赖

- JDK 17+（构建/运行后端）、Node.js ≥ 16（前端构建与零依赖评估脚本）、MySQL（本地密码默认 `000`，读 `data/.env` 的 `DB_*`）、XeLaTeX（TeX Live，需含中文字体）

### 4.2 初始化数据库（库名 X，8 + 2 + 1 = 11 张表）

以下命令默认在**项目根（README 所在目录，即 hello/）**执行：

```bash
mysql -u root -p000          # 密码 000 与 data/.env 默认值一致
mysql> source 1.sql;                                      # 建库建表 + 预置管理员（注意会 DROP 重建）
mysql> source migrations/add_notice_feedback_columns.sql; # notice 表追加定向/反馈字段
mysql> source migrations/create_notice_read_table.sql;    # notice_read 每用户已读表
mysql> source migrations/create_conversations_tables.sql; # conversations / conversation_messages（自 Node 迁移脚本归档）
mysql> source migrations/alter_api_log_duration_ms.sql;   # api_log 加 duration_ms（批次2/O1 耗时拆解）
mysql> source migrations/alter_api_log_error_type.sql;    # api_log 加 error_type（批次3/E2 失败归类）
mysql> exit;
```

### 4.3 配置并启动后端（终端 A）

```bash
cd spring-backend
# 配置自动读取 ../data/.env（DB/SMTP/LLM/Embedding 密钥），无需复制模板；JWT_SECRET 缺失会启动失败（fail-fast）
scripts\run.cmd               # 自动选 JDK 17+ 启动 jar（jar 不存在则先构建），默认 http://localhost:3000
# 开发模式用 scripts\mvn-run.cmd（mvn spring-boot:run）；详见 spring-backend/README.md §4
```

### 4.4 初始化 RAG 向量库（可选，首次运行需要）

```bash
cd spring-backend
scripts\rag_seed.cmd          # 写入内置模板库（通用图型示范 7 条）
# 也可用 rag_backfill.cmd 把历史生成回填为向量；rag_purge.cmd 清理污染向量
```

> 未配置 `EMBEDDING_*` 或调用失败时，RAG 自动降级为无检索提示词，主链路（生成→编译）不受影响。

### 4.5 启动前端（终端 B，在项目根）

```bash
npm install
npm run serve   # Vue CLI 开发服务器，默认 http://localhost:8080
```

### 4.6 验证

- 打开 `http://localhost:8080` 完成注册/登录
- 管理员：`http://localhost:8080` 登录页切「管理员登录」，账号 `admin123` / `666666`
- 在图表生成页输入示例如「绘制近五年出生人口条形图」→「生成 PDF」验证完整链路

```bash
cd spring-backend
scripts\verify.cmd            # 全量接口回归（最近一次 PASS=42 FAIL=0 WARN=0）
mvn test                      # 单测 39 例（LatexCompiler / ChartCodeValidator / Retriever / ChartCodeExtractor / StructuredOutputParser）
node eval/eval.mjs --tag=<tag> --model=qwen   # 离线评估集（需后端已启动）
```

> 生产部署（Linux/宝塔/pm2/nginx/TeX 安装坑）见 `docs/deployment.md`。

## 5. AI 生成链路（核心能力）

### 5.1 全链路

```
用户提问(±数据集)
  → ChatService.buildSystemPrompt
      ← RAG 召回 few-shot（模板库 + 个人历史；同题断链、重复系列不入库）
  → LlmClient：三通道降级链 qwen → siliconflow → deepseek（可停用/可跳过）
  → StructuredOutputParser（JSON 结构化输出）
      → ChartCodeExtractor（```latex 围栏块 / 裸代码兜底 + 字面 \n 转义还原）
  → LatexCompiler.preprocess（编译前五步确定性修复）
  → 异步编译任务队列（线程池 4 + 队列 100）→ Semaphore 限流（XeLaTeX 并发 ≤ 2）
  → PDF 落盘 + history JSON 落库（记录 model_used 等元数据）
```

### 5.2 RAG 检索增强（批次1 / 批次3.7）

- **实现**：`service/rag/` 下 `EmbeddingClient`（OpenAI 兼容 `/embeddings`，`BAAI/bge-m3`，1024 维）+ `VectorStore`（`rag_vector` 表，暴力余弦相似度，**不引入 pgvector 或外部向量库**）+ `Retriever` + `PromptComposer`（把召回结果拼成 few-shot 段落）。
- **两个分区**：`template`（内置通用图型示范，冷启动即有召回）与 `history`（用户历史生成，按 `user_id` 隔离）。
- **同题断链（防自污染）**：历史分区两道闸门——① 历史 `embed_text` 与本次提问**文字完全相同**即剔除；② 相似度 ≥ `app.rag.max-history-score`（默认 `0.98`）即剔除。**模板库传 `Double.MAX_VALUE`，不受影响。** 解决的是「模型照抄上一次结果、连错误一起复制」的自我强化循环。
- **入库准入**：`ChartCodeValidator.hasDuplicateSeries`（多系列坐标完全相同）的代码**不入库**，避免错误案例被当作 few-shot 范例反复喂回。
- **运维 CLI**：`scripts/rag_seed.cmd` / `rag_demo.cmd`（带 query 演示召回）/ `rag_backfill.cmd` / `rag_purge.cmd`。
- **降级**：embedding 未配置或调用失败 → `RagUnavailableException` → 自动退回无 RAG 提示词，**主链路不因 RAG 失败而失败**。

### 5.3 结构化输出 + 双层兜底

- 提示词要求模型先给 ```json（含 `chart_code` / `chart_type` 等字段）再给 ```latex；`StructuredOutputParser` 先按 JSON 解析，`ChartCodeExtractor` 再从围栏块或裸代码中提取。
- **转义还原**：模型常把换行输出成**字面 `\n`**（两字符）而非真实换行，会让整段 `tikzpicture` 挤成一行导致 XeLaTeX 报错。`ChartCodeExtractor.normalizeEscapes` 用「后不跟 ASCII 字母」的 lookahead + 「前不是反斜杠」的 lookbehind 还原，**不误伤 `\node` / `\text` / `\times` 等合法命令**。
- **空回复兜底**：主通道返回空 `content` 时按降级链接力；仍失败则返回 `502` 明确错误并写系统日志，不静默返回空图。

### 5.4 三通道降级链（批次3.5）

| 通道 | 模型 | 状态 |
|---|---|---|
| `qwen` | Qwen3.5（NSCC） | 启用（主通道） |
| `siliconflow` | GLM-4.9 | 启用（降级第二层） |
| `deepseek` | deepseek-v4-flash | 停用（余额为 0，`DEEPSEEK_ENABLED=false`） |

- 仅对「换通道可能成功」的错误接力（`402/408/429/5xx`，及消息含 `insufficient balance`/`quota`/`rate limit` 的 4xx）；`400/401/403` 等确定性错误**直接报错不重试**，避免把超时叠成双倍。
- 到链尾即止（**不回绕**）；`enabled=false` 或未配置 key 的层整层跳过。
- 实际完成通道写入响应 `data.model_used` 与历史 `metadata.model_used`，与请求通道不同即表示发生过降级（可观测、不静默）。

### 5.5 编译前确定性预处理（批次3.7）

模型写错的语法，**在编译前用确定性规则修好**，而不是把失败直接抛给用户：

| 步骤 | 触发判据 | 动作 / 解决的真实事故 |
|---|---|---|
| `dropInvalidColorKey` | `color={含逗号}` | 连同整行删除；`color=` 只接受单色，逗号列表被 xcolor 当成一个颜色名 → 每柱报错、刷屏并拖到 30s 超时 |
| `braceAtCoordinates` | `at=(x,y)` | → `at={(x,y)}`；未加花括号时逗号被 pgfkeys 当键分隔符 → `Runaway argument` 致命错误 |
| `stripEmptyAxes` | 全图无 `\addplot` | 删除 axis 环境；饼图被 axis 包裹会多画一个空坐标框，图元全落在框外 |
| `addYbarForHistogram` | 含「直方图/频数分布/分布图」且无 `ybar` | 在 `\begin{axis}[` 后插入 `ybar,`；只写 `fill=` 漏写 `ybar` 会渲染成面积覆盖图 |
| `ensureYmaxCoversData` | 显式 `ymax` < 数据最大值 | 抬到 `ceil(最大值 × 1.1)`；未写 `ymax` 或已够大则**不动** |

- 另有全角逗号归一、脚手架行清除等基础归一。
- **文档外壳 xcolor**：命名色（`SteelBlue`/`MidnightBlue` 等 svgnames/dvipsnames）必须在加载 `pgfplots` **之前**通过 `\PassOptionsToPackage{dvipsnames,svgnames}{xcolor}` 声明（`xcolor` 已被 tikz/pgfplots 提前加载，写成 `\usepackage[dvipsnames]{xcolor}` 会触发 `Option clash`）。
- **实测**：`hist351` 样本预处理后 xelatex 报错数 **100 → 0**，渲染正常。

### 5.6 异步编译队列与并发控制（批次2 / 批次3.6）

- 编译改为**异步任务契约**：`POST /api/compile/{history_id}` 立即返回 `task_id`，`GET /api/compile/task/{task_id}` 轮询至终态（含 `pdf_path` / `duration_ms`），PDF 由 `GET /api/compile/{id}/pdf` 按归属鉴权流式返回。
- 线程池 `maxPoolSize=4`、队列容量 `COMPILE_QUEUE_CAPACITY`（默认 `100`，**可注入**）；满载时返回 **503** 并把 `COMPILE_QUEUE_FULL` 落库（任务不静默丢弃，有硬证据）。
- XeLaTeX 进程并发上限 `LATEX_MAX_CONCURRENCY=2`（`Semaphore` 限流），单次编译 30s 超时，临时目录必清理。

### 5.7 离线评估集与量化结果

- **位置**：`spring-backend/eval/` —— `cases.json`（16 例：柱状 / 折线 / 饼图 / 散点 / 堆叠 / 密集柱 / 误差棒 / 双系列 / 负值 / 双轴 / 堆叠面积 / 歧义提问 / 无数据提问）、`eval.mjs`（零第三方依赖）、`violations.mjs`（静态违例检测）。
- **指标定义**：首轮通过率 = `chat 成功 && chart_code 非空 && 编译成功`；零违例通过率 = 在此之上再要求 `detectViolations` 为空。

最近一次结果（`eval/results/v3.0-qwen-rag-on.json`，2026-09-14，通道 `qwen`）：

| 指标 | 值 |
|---|---|
| 用例数 | 16 |
| 生成成功率 / 可编译率(占生成) | 1.0 / 1.0 |
| 首轮通过率 | 1.0 |
| 零违例通过率 | 1.0（`violation_counts` 为空） |
| 平均 / P95 chat 耗时 | 6687ms / 10969ms |
| 降级发生次数 | 0 |

- **规则版本与口径**：`violations.mjs` 当前静态覆盖 **R1–R3、R5–R9、R12**（R9 与 `ChartCodeValidator` 同源，保证「评估口径」与「入库准入」一致）。
- R4（数值与坐标轴量纲一致）**刻意不做静态检测**：需要理解数据语义标度，正则会大量误报，保留人工评审。
- R10（直方图须 ybar）/ R11（`ymax` 覆盖数据）**刻意不做静态检测**：二者已被编译前预处理确定性修复，静态检测会恒为零违例，只会让指标虚高、失去区分度——**评估指标不能自我欺骗**。
- 历史结果（`v2.1-qwen-rag-on` 8605ms、`v2-qwen-rag-on/off`、`v2-rag-*`、`rag-*`）全部保留在 `results/`；**规则或模型变更后结果跨版本不可比**，引用时须带上 tag。

## 6. 核心功能与模块速览

### 6.1 用户能力

| 能力域 | 后端入口 | 前端页面 | 说明 |
|---|---|---|---|
| 认证 | `controller/AuthController` + `security/JwtAuthenticationFilter` | Login/Register/AdminLogin、ChangeInformation | 注册（邮箱验证码）/登录/改密码用户名邮箱；JWT 用户 24h、管理员 7d |
| AI 生成 | `controller/ChatController`（`service/ChatService` + `service/rag/*`） | ChartGenerator | RAG 召回 → 三通道降级生成 PGFPlots 代码并落库；空回复返回 502 明确错误 |
| 编译 | `controller/CompileController`（`service/CompileTaskService`、`service/CompileService`） | ChartGenerator | 异步任务队列 + `preprocess` 预处理 → XeLaTeX 编译 → 更新 PDF 路径 |
| 历史 | `controller/HistoryController` | MyHistory | 列表/详情/删除（事务级联 api_log）/统计/CSV 导出 |
| 数据集 | `controller/DatasetController` | DataUpload、ChartGenerator | 上传 ≤100MB/列表/改名/删除/下载 |
| 多轮会话 | `controller/ConversationController` | ChartGenerator | 会话 CRUD + 消息读取；chat 生成时联动落 `conversation_messages` |
| 反馈 | `controller/FeedbackController` | MyFeedback | 提交/查看反馈；管理员回复后自动生成定向通知 |
| 通知 | `controller/NoticeController` | MyNotice、导航栏 | 系统广播+反馈回复统一列表，已读走 `notice_read`，导航栏未读红点 |
| 验证码 | `controller/VerificationController` + `service/VerificationService` | Register/ChangeInformation | 6 位验证码，10 分钟有效、一次性 |

### 6.2 管理员能力（`/admin/*` 页面，`src/views/` + `src/Admin.vue`）

| 能力域 | 后端入口 | 前端页面 | 说明 |
|---|---|---|---|
| 概览 | `controller/AdminStaticController` | Admin.vue | 全库计数（用户/文件/生成/反馈） |
| 用户管理 | `controller/AdminUserController` | AdminUser | 列表/重置密码为 666666/删除（级联）/统计 |
| 通知管理 | `controller/AdminNoticeController` | AdminNotice | 系统通知 CRUD（广播 target_user_id=NULL）+ 已读人数 |
| 系统监控 | `controller/AdminLogController` | AdminLog | API 统计（耗时聚合/失败归类/按日耗时序列）/系统日志/健康概览/表状态；三张 ECharts 图在 `onUnmounted` 中先 disconnect 观察者再 dispose 实例 |
| 反馈处理 | `controller/FeedbackController`（管理员角色校验） | AdminFeedback | 列表/详情/回复/删除，回复联动写通知 |

> 模块/数据流/ER 详图见 `docs/architecture.md`；完整接口定义见 `docs/openapi.yaml`。

## 7. 配置要点

| 配置 | 位置 | 事实说明 |
|---|---|---|
| 环境变量 | `data/.env`（Java 启动时经 `application.yml` 自动导入，可用环境变量覆盖） | `JWT_SECRET`、`SMTP_*`（QQ 授权码）、`NSCC_*`（Qwen3.5）、`SILICONFLOW_*`（GLM-4.9）、`DEEPSEEK_*`（停用）、`EMBEDDING_*`（RAG 向量化），以及各通道 `*_ENABLED` 开关；详见 `spring-backend/README.md` §5 |
| 后端地址 | `src/config.js` 的 `API_BASE_URL` | 默认 `http://localhost:3000`；前端所有页面统一从这里取值 |
| 数据库连接 | `data/.env` 的 `DB_HOST/DB_USER/DB_PASSWORD/DB_NAME` | 未设置时回退本地默认值 `localhost/root/000/X`（`application.yml`） |
| 编译与队列 | `LATEX_MAX_CONCURRENCY`（2）、`COMPILE_QUEUE_CAPACITY`（100）、`LATEX_TIMEOUT_MS`（30000） | 见 §5.6；队列容量调小可用于演示「队列满 → 503」 |
| RAG | `app.rag.enabled`、`app.rag.max-history-score`（0.98）、`app.embedding.api-key/api-url/model/dim` | 未配置 embedding 时自动降级为无 RAG；向量写 `rag_vector` 表 |

## 8. 安全与错误处理要点

- JWT 鉴权：`security/JwtAuthenticationFilter` 验签并查库装配角色（延续「不信任 JWT 中角色声明」策略）；数据隔离按 `user_id = ?`
- 密码：BCrypt 哈希；规则「仅字母数字、6–16 位」（`AuthService` 校验）
- SQL：全部参数化查询
- LaTeX 安全：编译前 `validate` 拦截危险序列（`\write18` / `\input` / `\usepackage` / `\includegraphics` 等）与超长代码
- AI 失败可见：空回复/上游错误按降级链接力，仍失败返回 `502` 并写系统日志；`model_used` 记录实际完成通道，降级不静默
- 编译失败：返回 stderr、临时目录 `safeCleanup` 必清理
- ✅ 已加固（2026-09-07，详见 `docs/log.md`）：`/api/admin/*` 四个模块统一挂 `authenticateToken + requireAdmin`；PDF 移除 `/storage` 无鉴权静态托管，改 `GET /api/compile/:id/pdf` 归属校验流式返回；LaTeX 编译前危险序列校验；数据库凭据统一改读 `.env`（缺省回退本地默认）；JWT 去除兜底密钥（缺失启动即退出）；密码下限 6 位
- ⚠️ 运维注意：`.env` 含 SMTP/LLM/Embedding 密钥须保密勿提交；`1.sql` 预置管理员哈希未经明文验证（重置密码统一 `666666` 见 `service/AdminUserService`）

## 9. 版本说明

| 版本 | 日期 | 说明 |
|---|---|---|
| v3.7 | 2026-09-14 | **生成质量硬修复 + 规则口径对齐**：RAG 同题断链（文字相同 / 相似度 ≥0.98 剔除）+ 入库准入 + `rag_purge` 清理 9 条污染向量；`LatexCompiler.preprocess` 五步确定性修复；提示词升 `v1.4-pie-no-axis`（R9–R12 + 饼图专项）；修复文档外壳 xcolor 选项位置（命名色恢复渲染）；`eval/violations.mjs` 补 R9/R12 与提示词口径对齐；README 补齐 AI 链路与实测数据，重跑评估集产出 `v3.0-qwen-rag-on`（16 例全通过、零违例） |
| v3.6 | 2026-09-14 | **压平转义还原 + 生命周期 + 队列满验收**：`ChartCodeExtractor.normalizeEscapes` 修复字面 `\n`（`bar_dense` 首轮通过率 0.938 → 1.0）；`AdminLog.vue` 三个 `ResizeObserver` 登记与 `onUnmounted` 释放；`COMPILE_QUEUE_CAPACITY` 可注入并实测「队列满 → 503 + 落库」 |
| v3.5 | 2026-09-14 | **模型三通道 + 降级链修复**：新增 `siliconflow` 通道；降级链从「仅空回复触发」改为「硬错误按 `qwen → siliconflow → deepseek` 接力」（此前 16 连败的根因）；通道 `enabled` 开关；`model_used` 可观测；`verify.js` 新增降级链 stub 断言 |
| v3.4 | 2026-09-13 | **目录整理**：清理残留垃圾（backend.log/texput.log/空目录等）；共享数据目录 `backend/` 改名为 `data/`（配置、DB `generation_path` 前缀、文档同步更新），语义更清晰 |
| v3.3 | 2026-09-13 | **Node 后端退役**：删除 `hello/backend/` 下全部 Node/Express 代码（app.js/routes/services 等），`backend/` 原地保留为共享运行时数据目录（`.env` + `uploads/` + `storage/`）；唯一后端为 `spring-backend/`；`conversations` 建表 DDL 归档至 `migrations/create_conversations_tables.sql`（详见 `docs/log.md`） |
| v3.2 | 2026-09-13 | **新增 Java 后端**：`spring-backend/`（Spring Boot 3.2 + MyBatis-Plus 3.5）全量重写 Node/Express 后端（13 路由 / 11 表 / JWT 鉴权 / AI 生成 / XeLaTeX 编译 / 上传 / 邮件 / 管理后台），响应统一 `{success,data,message}` 并同步适配前端；Node 后端保留待退役（详见 `docs/log.md` 2026-09-13） |
| v3.1 | 2026-09-07 | **全量 P0 安全加固 + 配置修复**：`/api/admin/*` 统一挂 `authenticateToken + requireAdmin`；PDF 移除无鉴权静态托管改归属校验流式返回（前端 Blob 预览）；LaTeX 编译前危险序列校验；数据库凭据改读 `.env`（缺省回退本地默认）；JWT 去除兜底密钥（缺失启动即退出）；密码 6-16 位；修复 `.env` 旧占位 `DB_*` 导致的本地连库失败（详见 `docs/log.md` 2026-09-07） |
| v3.0 | 2026-09-05 | **精简重构为总入口**；修正与代码不一致处（如后端路由前缀实为 **13 个**，非 v2.0 所述 14 个）；详细 API/部署/架构内容迁至 docs/ 专项文档，避免多份重复维护 |
| v2.0 | 2026-07-16 | 旧版主文档（API 明细等已迁移，其「与旧文档差异」并入 `docs/architecture.md` §7） |

**预置管理员账号**：`admin123` / `666666`（`1.sql:14-20`）。

---
**文档版本**：3.7
**最后更新**：2026-09-14
**基准**：`hello/` 下实际源码、SQL/迁移文件，以及 `spring-backend/verify.js`、`mvn test`、`eval/results/*.json` 的实测输出
