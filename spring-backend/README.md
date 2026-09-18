# PGFPlotsGenerator Java 后端（spring-backend）

> Spring Boot 3 + MyBatis-Plus 后端（**本项目唯一后端**；原 Node/Express 版已于 2026-09-13 退役删除，接口路径与行为等价），
> 共享同一套 MySQL 库（`X`，12 张表 = 8 基础表 + notice_read + conversations 两表 + rag_vector）与同一 Vue 3 前端，仅需切换 `src/config.js` 的 `API_BASE_URL`。

## 1. 技术栈

| 层 | 选型 |
|---|---|
| 语言/构建 | Java 17（构建用 JDK 21）+ Maven |
| 框架 | Spring Boot 3.2.12（web / security / validation / mail / aop） |
| ORM | MyBatis-Plus 3.5.5（复杂聚合查询走 `resources/mapper/*.xml`） |
| 鉴权 | Spring Security 无状态 JWT（jjwt 0.12.6）+ BCrypt |
| 文件解析 | Apache POI 5.2.5（xlsx）+ 原生读取（csv/txt） |
| HTTP 客户端 | Spring 6 `RestClient`（三通道共用 OpenAI 兼容 `/chat/completions`：Qwen3.5 / THUDM/GLM-4-9B-0414 / DeepSeek） |
| RAG 检索 | 自实现向量检索：`EmbeddingClient`（`/embeddings`，`bge-m3` 1024 维）+ `VectorStore`（`rag_vector` 表，暴力余弦）+ `Retriever` + `PromptComposer` |
| 邮件 | spring-boot-starter-mail（JavaMailSender） |
| 编译 | `ProcessBuilder` 调 XeLaTeX（30s 超时）+ 编译前 `preprocess` 12 条确定性修复规则 + 异步任务队列与 `Semaphore` 限流 |

## 2. 与 Node 版的映射（历史对照：Node 代码已于 2026-09-13 删除）

| Node | Java |
|---|---|
| `db.js` mysql2 连接池 | HikariCP + MyBatis-Plus |
| `middleware/auth.js` | `security/JwtAuthenticationFilter` + `SecurityConfig`（角色查库装配） |
| `jsonwebtoken` | `JwtTokenProvider`（用户 24h / 管理员 7d，claims 与 Node 一致） |
| `bcryptjs` / `bcrypt` | `BCryptPasswordEncoder` |
| `routes/*.js`（13 个） | `controller/*` + `service/*` |
| `services/verificationService.js` | `service/VerificationService` |
| `utils/systemLog.js` | `util/SystemLogWriter` |
| multer | Spring `MultipartFile`（≤100MB，`spring.servlet.multipart`） |
| xlsx | POI（`util/FileContentReader`） |
| nodemailer | JavaMailSender |
| `child_process.exec(xelatex)` | `util/LatexCompiler`（ProcessBuilder） |
| openai SDK / axios | `client/LlmClient`（统一 OpenAI 兼容 `/chat/completions`） |

### 端点对照（路径完全不变）

`/api/auth`、`/api/verification`、`/api/datasets`、`/api/chat`、`/api/compile`、`/api/history`、
`/api/conversations`、`/api/feedback`、`/api/notice`、`/api/admin/{users,notices,log,static}`。

## 3. 统一响应契约

所有 JSON 接口统一为：

```json
{ "success": true, "data": { }, "message": null }
```

- 原 `{code,message,data}`（notice.js、AdminNotice.js）与 `{success,code,message,data}`（AdminUser.js）已统一。
- HTTP 状态码语义保留：400/401/403/404/429/500/502。
- 主要结构变化：登录/注册的 `user`、`token` 由顶层移入 `data`；文件上传/更新返回统一 `DatasetVO`；反馈列表 `data` 由数组改为 `{records, pagination}`。

## 4. 构建与运行

> ⚠️ **常见坑**：直接 `mvn package` 报 `无效的标记: --release`，是因为 Maven 使用的 `JAVA_HOME` 是 **JDK 8**；`--release` 需 JDK 9+，而 Spring Boot 3 需 **JDK 17+**。用下面的一键脚本可自动选中 JDK 17/21。

### 4.1 一键脚本（推荐）

```bat
cd hello\spring-backend
scripts\build.cmd     :: 自动选 JDK 17+ 并 mvn clean package
scripts\run.cmd       :: 自动选 JDK 17+ 并启动 jar（jar 不存在则先构建）
scripts\mvn-run.cmd   :: 自动选 JDK 17+ 并执行 mvn spring-boot:run（开发模式）
scripts\verify.cmd    :: 启动 + 全量接口回归，输出 PASS/FAIL（最近一次 PASS=42 FAIL=0）
scripts\rag_seed.cmd  :: 写入 RAG 内置模板库（另有 rag_demo / rag_backfill / rag_purge）
```

脚本依次在以下位置挑选 JDK：`%PG_JAVA_HOME%` → `C:\Program Files\Microsoft\jdk-21*` → `C:\Program Files\Java\jdk-21` → `...\jdk-17` → `...\jdk-23`。
如路径不同，可先 `set "PG_JAVA_HOME=D:\path\to\jdk21"` 再运行脚本。

### 4.2 手动命令

```bat
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.2.13-hotspot"   :: 必须 JDK 17+
cd hello\spring-backend
mvn -DskipTests package
java -jar target\pgfplots-backend-1.0.0.jar

:: 开发模式（同样必须先切 JAVA_HOME，否则报 class file version 61.0 / --release 错误）
mvn spring-boot:run
```

> ⚠️ **第二个常见坑**：直接 `mvn spring-boot:run` 报
> `RunMojo has been compiled by a more recent version of the Java Runtime (class file version 61.0) ... only recognizes up to 52.0`
> —— 同样是 Maven 跑在 **JDK 8** 上（61.0=Java 17，52.0=Java 8），Spring Boot 3 插件要求 Maven 自身运行在 JDK 17+。
> 解决：先 `set "JAVA_HOME=...jdk-21..."`，或直接用 `scripts\mvn-run.cmd`。

### 4.3 VS Code 直接运行（开发期推荐，不用脚本）

仓库根 `.vscode/` 目前仅提供 `settings.json`（**无 `launch.json`**）：

1. 用 VS Code 打开仓库根目录，装好 Java 扩展；
2. 通过「运行和调试」直接启动 `PgApplication` 时，务必把**工作目录固定为 `hello/spring-backend`**——否则读不到 `../data/.env`、存储目录也会跑偏；控制台建议用集成终端（UTF-8，中文日志不乱码）；
3. 如本机 JDK 非 `C:\Program Files\Microsoft\jdk-21.0.2.13-hotspot`，需先 `set "JAVA_HOME=..."` 或改脚本中的候选路径。

纯终端等价方式（不用 `scripts\run.cmd`）：

```bat
chcp 65001                                          :: 中文日志不乱码
cd hello\spring-backend
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.2.13-hotspot"
mvn spring-boot:run                                 :: 或 java -jar target\pgfplots-backend-1.0.0.jar
```

**配置来源**：默认自动读取 `..\data\.env`（DB/密钥/SMTP 配置，位于共享数据目录 `data/` 中），因此**请在 `spring-backend` 目录下启动**（IDE 里务必把 working directory 设成它，否则读不到 `.env`、存储目录也会跑偏）；`JWT_SECRET` 缺失会启动失败（fail-fast）。

前端：`hello/` 目录 `npm run serve`，`src/config.js` 的 `API_BASE_URL` 默认 `http://localhost:3000`，与 Java 后端默认端口一致。

## 5. 配置（环境变量覆盖 application.yml）

**配置优先级**：命令行环境变量 > `..\data\.env`（自动导入，`optional`）> `application.yml` 默认值。
因此 DB 连接、LLM / Embedding 密钥、SMTP 统一放在共享数据目录 `data/.env` 中，无需重复配置；文件不存在时回退下表默认值。

| 变量 | 默认 | 说明 |
|---|---|---|
| `PORT` | 3000 | 服务端口 |
| `JWT_SECRET` | 空（必填） | JWT 密钥，缺失启动失败 |
| `DB_HOST/DB_PORT/DB_USER/DB_PASSWORD/DB_NAME` | localhost/3306/root/000/X | 与 `data/.env` 默认值一致 |
| `SMTP_*` | — | 邮件验证码 |
| `NSCC_API_KEY/URL`、`SILICONFLOW_API_KEY/URL/MODEL`、`DEEPSEEK_API_KEY/URL/MODEL` | — | 三个模型通道（见下） |
| `EMBEDDING_API_KEY/URL/MODEL/DIM` | — / `https://api.siliconflow.cn/v1` / `BAAI/bge-m3` / `1024` | RAG 向量化（**与生成通道的 key 分开**，见下注） |
| `QWEN_ENABLED` / `SILICONFLOW_ENABLED` / `DEEPSEEK_ENABLED` | `true` | 通道开关；`false` 时该层整层跳过且不可选为主模型（DeepSeek 余额为 0 时可置 `false`；当前本地 `data/.env` 为 `true`） |
| `UPLOADS_DIR` | `../data/uploads` | 数据集目录（复用历史文件） |
| `HISTORY_DIR` | `../data/storage/history` | 生成记录 JSON |
| `CHARTS_DIR` | `../data/storage/generated_charts` | 编译产物 PDF |
| `RELATIVE_BASE` | `..` | `generation_path` 相对路径解析基准 |
| `XELATEX` / `LATEX_TIMEOUT_MS` | `xelatex` / 30000 | 编译器与超时 |
| `LATEX_MAX_CONCURRENCY` | `2` | XeLaTeX 并发编译上限（批次2/G2，`Semaphore` 限流） |
| `COMPILE_QUEUE_CAPACITY` | `100` | 编译任务队列容量（批次3.6 起可注入）；满载阈值 = `maxPoolSize(4) + 该值`，调小可用于验证「队列满 → 503」 |
| `RAG_ENABLED` | `false` | RAG 检索增强总开关（当前本地 `data/.env` 为 `true`；开启需配置 `EMBEDDING_*` 并执行 `rag_seed` / `rag_backfill`） |
| `RAG_MIN_SCORE` / `RAG_MAX_HISTORY_SCORE` | `0.55` / `0.98` | 召回下限 / 历史分区相似度**上限**（≥ 上限视为同题，剔除以防照抄上一次的错误） |
| `RAG_MIN_QUALITY` | `verified` | 历史分区最低可召回等级（`golden`/`verified`/`unverified`）；设 `unverified` 等价不过滤；模板分区恒不受影响 |
| `RAG_TIMEOUT_MS` | `3000` | embedding 调用超时；超时或失败自动降级为无 RAG 提示词 |

### 模型通道与降级链（批次3.5）

三个通道共用同一个 OpenAI 兼容客户端（`LlmClient`），仅配置不同：

| 通道 key | 默认模型 | 默认地址 | 当前状态 |
|---|---|---|---|
| `qwen` | `Qwen3.5` | NSCC（`NSCC_API_URL`） | 启用（主通道） |
| `siliconflow` | `THUDM/GLM-4-9B-0414` | `https://api.siliconflow.cn/v1` | 启用（免费档，降级链第二层） |
| `deepseek` | `deepseek-v4-flash` | `https://api.deepseek.com/v1` | 开关控制（`DEEPSEEK_ENABLED`，当前本地 `data/.env` 为 `true`；余额为 0 时置 `false` 整层跳过） |

降级规则：

- 固定优先级 `qwen → siliconflow → deepseek`；主通道失败后**从其之后**逐个尝试，到链尾即止（**不回绕**）。
- 只对「换通道可能成功」的错误接力：`402`/`408`/`429`/`5xx`，以及消息含 `insufficient balance`/`quota`/`rate limit` 的 4xx；
  `400`/`401`/`403` 等确定性错误**直接报错、不重试**（避免把 45s 超时叠成 90s）。
- `enabled=false` 或未配置 key 的层**整层跳过**，不发请求（空 key 请求会被上游回 401，误记成上游鉴权失败）。
- 响应 `data.model_used` 与历史 `metadata.model_used` 记录**实际完成通道**；与请求的 `model` 不同即表示发生过降级。
- 已知取舍：主模型选 `siliconflow` 时兜底仅剩 `deepseek` 一层；若 `DEEPSEEK_ENABLED=false` 则**无兜底**。

> 注：`siliconflow` 用于生成的 key 与 embedding 用的 `EMBEDDING_API_KEY` **须分开**，避免配额混用后无法定位问题。

### RAG 检索增强（批次1 / 批次3.7）

`app.rag.enabled=true` 时，`ChatService.buildSystemPrompt` 会先经 `service/rag/` 召回 few-shot 再拼进 system 提示词：

- **实现**：`EmbeddingClient`（OpenAI 兼容 `/embeddings`）+ `VectorStore`（`rag_vector` 表，暴力余弦，**不引入 pgvector**）+ `Retriever` + `PromptComposer`；分区 `template`（内置示范）与 `history`（按 `user_id` 隔离）。
- **同题断链**：历史分区剔除「`embed_text` 与本次提问文字完全相同」及「相似度 ≥ `app.rag.max-history-score`（默认 0.98）」的记录；**模板库不受影响**。用于切断「照抄上一次结果、连错误一起复制」的自我强化循环。
- **入库准入**：`util/ChartCodeValidator.hasDuplicateSeries`（多系列坐标完全相同）的代码不入库，避免错误案例被当作范例反复喂回。
- **语料分级准入（v3.9 / 2026-09-16）**：`rag_vector.quality` 分 `golden`（模板库）/ `verified`（编译成功 + 静态零违例，`--rag-cli=verify:<userId>` 离线定级）/ `unverified`（默认，不进召回）；`app.rag.min-quality`（默认 `verified`）只过滤历史分区，过滤后为空自动回退空召回。详见 `docs/rag-corpus-quality.md`。
- **CLI**：`scripts/rag_seed.cmd`（模板库）/ `rag_demo.cmd`（带 query 看召回）/ `rag_backfill.cmd`（历史回填）/ `rag_purge.cmd`（清理污染向量）。
- **降级**：未配置 `EMBEDDING_*`、超时或调用失败 → 自动退回无 RAG 提示词，**主链路不因 RAG 失败而失败**。

## 6. 包结构

```
com.pg.pgfplots
├── common/     Result / BusinessException / GlobalExceptionHandler
├── config/     SecurityConfig / MybatisPlusConfig / WebConfig / AppProperties
├── security/   JwtTokenProvider / JwtAuthenticationFilter / LoginUser / SecurityUtils
├── entity/     12 张表实体
├── mapper/     MyBatis-Plus Mapper（+ resources/mapper/*.xml）
├── dto/        请求/响应 DTO（字段名保持 Node 契约）
├── client/     LlmClient（三通道统一 OpenAI 兼容调用）
├── util/       FileStorage / FileContentReader / LatexCompiler（含 preprocess 12 条确定性修复规则）/ ChartCodeExtractor（含字面 \n 还原）/ ChartCodeValidator（RAG 入库准入）/ StructuredOutputParser / PromptTemplates（R1–R16，v1.5-coord-color-note）/ SystemLogWriter / TimeFormat
├── service/    业务服务（ChatService 三通道降级链 / CompileService / CompileTaskService 异步编译队列 / VerificationService …）
│   └── rag/    EmbeddingClient / VectorStore / Retriever / PromptComposer / RagService / RagQuality（语料分级）
├── tools/      RagCli（--rag-cli=seed|demo|backfill|purge|verify）/ RagTemplates（内置模板库 SEEDS 21 条）
└── controller/ 13 个控制器
```

## 7. 如何验证功能是否完成

### 7.1 一键自动验证（推荐）

```bat
cd hello\spring-backend
scripts\build.cmd        :: 先确保能构建
scripts\verify.cmd       :: 自动启动后端 + 回归全部接口，输出 PASS/FAIL
mvn test                 :: 单测 79 例（LatexCompiler 31 / ChartCodeValidator 20 / Retriever 12 / ChartCodeExtractor 11 / StructuredOutputParser 5）
```

`verify.js` 覆盖：鉴权（401/403/管理员登录）、管理后台（static / users / notices / log 统计，含 **responseTime 真实聚合、errorTypes 失败分类、responseTimeSeries 按日耗时序列**三条断言）、用户侧（notice / history / conversations / feedback / validate）、**数据集上传·列表·改名·下载·删除**、**XeLaTeX 异步编译（提交 task_id → 轮询终态 → duration_ms）+ PDF 鉴权流式返回**、**降级链语义**（内置 Node `http` stub，零密钥零外网：可降级 500 接力成功 / 不可降级 401 不重试 / 不传 model 回落主通道）、**编译队列满 → 503 且落库 `COMPILE_QUEUE_FULL`**。

**最近一次结果（2026-09-14）：`PASS=42  FAIL=0  WARN=0`，全部通过。**

离线评估集：`node eval\eval.mjs --tag=<tag> --model=qwen`（需后端已启动）；最近一次 `v3.0-qwen-rag-on` 16 例 —— 生成成功率 / 可编译率 / 首轮通过率 / 零违例通过率均 1.0，平均 6687ms、P95 10969ms，结果见 `eval/results/`。

### 7.2 邮件验证码（需人工，会真实发信）

```bat
curl -X POST http://localhost:3000/api/verification/send-register-code -H "Content-Type: application/json" -d "{\"email\":\"<待注册邮箱>\"}"
```

### 7.3 前端联调

`hello/` 执行 `npm run serve`，打开 http://localhost:8080，用 `admin123/666666` 登录管理端，或注册普通用户，走「生成 → 编译 → 预览 PDF」。

### 7.4 已实测结论

| 项 | 结果 |
|---|---|
| 启动 | `Started PgApplication`（Tomcat :3000）；启动日志打印各存储目录的绝对路径 |
| 鉴权 | 无 token → 401 `{success:false,message:"未提供token"}`；非管理员访问 `/api/admin/**` → 403 |
| 登录 | 管理员 `admin123/666666` → `data.{user,token}` |
| 数据查询 | 13 个前缀接口返回结构正确 |
| 数据集 | 上传/列表/改名/下载/删除 全通过 |
| 编译 | `POST /api/compile/82` → `data.{task_id,status:"queued",history_id}`（立即返回）；`GET /api/compile/task/{task_id}` 轮询至 `success`（含 `pdf_path/file_size/duration_ms`）；`GET /api/compile/82/pdf` → 200 流式 PDF |
| 降级链 | 主通道 500 → 接力 `siliconflow` 成功且 `model_used=siliconflow`；401 确定性错误不重试、备用通道调用次数 0（本地 stub 断言，零密钥零外网） |
| 编译队列 | `queue-capacity=1` 时并发提交 12 个编译 → 确实返回 503「队列已满」，且被拒任务落库 `COMPILE_QUEUE_FULL` |
| UTF-8 | 中文正常（`curl.exe` 原始字节校验） |

## 8. 退役 Node 后端（✅ 已于 2026-09-13 执行完成）

> **状态：Node/Express 后端代码已全部删除。** 其数据目录 `backend/` 已于同日改名为 `data/`，现为共享运行时数据目录，仅含 `.env`、`uploads/`、`storage/`，**Java 仍在读取，请勿删除**。`conversations` 建表 DDL 已归档至 `hello/migrations/create_conversations_tables.sql`。

### 8.1 与 Node 共存/冲突说明（已核对，无冲突）

| 项 | 说明 |
|---|---|
| 数据库 | Java 通过 `..\data\.env` 读取同一份 `DB_*`，连的是**同一个库 `X`**，**无需迁移数据、无需改 schema** |
| `data/uploads` | Java 默认 `UPLOADS_DIR=../data/uploads`；历史文件与新增上传文件均在此目录，29 个历史数据集照常可读 |
| `data/storage` | Java 默认 `HISTORY_DIR=../data/storage/history`、`CHARTS_DIR=../data/storage/generated_charts`；`generation_path` 以 `RELATIVE_BASE=..` 解析，**历史 PDF 可直接读取**（DB 前缀已同步改为 `data\`） |
| 端口 | 默认 **3000**；退役后仅剩 Java 一个后端，前端 `API_BASE_URL` 无需修改 |

启动日志会打印解析后的绝对路径，便于确认（见 §7.4）。

### 8.2 已执行的退役步骤（存档）

1. ✅ 确认前端 `API_BASE_URL` 指向 Java 后端且联调通过（`scripts\verify.cmd`，**当时基线** `PASS=27 FAIL=0`）。
2. ✅ 保留 `.env`、`uploads/`、`storage/`（Java 复用；该目录随后改名为 `data/`，见 §8 开头）。
3. ✅ 归档 `backend/migrations/001_conversations.js` 的建表 DDL 为 `hello/migrations/create_conversations_tables.sql`。
4. ✅ 删除 `backend/` 下全部 Node 代码：`app.js`、`db.js`、`createAdmin.js`、`routes/`、`middleware/`、`services/`、`utils/`、`migrations/001_conversations.js`、`package.json`、`package-lock.json`、`yarn.lock`、`.env.example`、`node_modules`。
5. ✅ 同步更新 `hello/README.md`、`docs/architecture.md`、`docs/development.md`、`docs/deployment.md`、`.github/copilot-instructions.md` 的后端描述。
