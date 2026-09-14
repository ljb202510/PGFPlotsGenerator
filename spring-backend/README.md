# PGFPlotsGenerator Java 后端（spring-backend）

> Spring Boot 3 + MyBatis-Plus 后端（**本项目唯一后端**；原 Node/Express 版已于 2026-09-13 退役删除，接口路径与行为等价），
> 共享同一套 MySQL 库（`X`，11 张表，schema 不变）与同一 Vue 3 前端，仅需切换 `src/config.js` 的 `API_BASE_URL`。

## 1. 技术栈

| 层 | 选型 |
|---|---|
| 语言/构建 | Java 17（构建用 JDK 21）+ Maven |
| 框架 | Spring Boot 3.2.12（web / security / validation / mail / aop） |
| ORM | MyBatis-Plus 3.5.5（复杂聚合查询走 `resources/mapper/*.xml`） |
| 鉴权 | Spring Security 无状态 JWT（jjwt 0.12.6）+ BCrypt |
| 文件解析 | Apache POI 5.2.5（xlsx）+ 原生读取（csv/txt） |
| HTTP 客户端 | Spring 6 `RestClient`（调用 DeepSeek / Qwen3.5） |
| 邮件 | spring-boot-starter-mail（JavaMailSender） |
| 编译 | `ProcessBuilder` 调 XeLaTeX（30s 超时） |

## 2. 与 Node 版的映射

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
build.cmd     :: 自动选 JDK 17+ 并 mvn clean package
run.cmd       :: 自动选 JDK 17+ 并启动 jar（jar 不存在则先构建）
mvn-run.cmd   :: 自动选 JDK 17+ 并执行 mvn spring-boot:run（开发模式）
verify.cmd    :: 启动 + 全量接口回归，输出 PASS/FAIL
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
> 解决：先 `set "JAVA_HOME=...jdk-21..."`，或直接用 `mvn-run.cmd`。

### 4.3 VS Code 直接运行（开发期推荐，不用脚本）

仓库根已提供 `.vscode/launch.json` 与 `.vscode/settings.json`：

1. 用 VS Code 打开仓库根目录，装好 Java 扩展；
2. 在「运行和调试」里选 **PG 后端（spring-backend，端口 3000）** 直接启动 —— 该配置已把**工作目录固定为 `hello/spring-backend`**，控制台用集成终端（UTF-8，中文日志不会乱码）；
3. `settings.json` 已把 JDK 21 设为默认运行时（`C:\Program Files\Microsoft\jdk-21.0.2.13-hotspot`），如本机路径不同请改成自己的。

纯终端等价方式（不用 `run.cmd`）：

```bat
chcp 65001                                          :: 中文日志不乱码
cd hello\spring-backend
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.2.13-hotspot"
mvn spring-boot:run                                 :: 或 java -jar target\pgfplots-backend-1.0.0.jar
```

**配置来源**：默认自动读取 `..\data\.env`（DB/密钥/SMTP 配置，位于共享数据目录 `data/` 中），因此**请在 `spring-backend` 目录下启动**（IDE 里务必把 working directory 设成它，否则读不到 `.env`、存储目录也会跑偏）；`JWT_SECRET` 缺失会启动失败（fail-fast）。

前端：`hello/` 目录 `npm run serve`，`src/config.js` 的 `API_BASE_URL` 默认 `http://localhost:3000`，与 Java 后端默认端口一致。

## 5. 配置（环境变量覆盖 application.yml）

**配置优先级**：命令行环境变量 > `..\backend\.env`（自动导入，`optional`）> `application.yml` 默认值。
因此 DB 连接、LLM 密钥、SMTP 与 Node 版共用同一份 `.env`，无需重复配置；文件不存在时回退下表默认值。

| 变量 | 默认 | 说明 |
|---|---|---|
| `PORT` | 3000 | 服务端口 |
| `JWT_SECRET` | 空（必填） | JWT 密钥，缺失启动失败 |
| `DB_HOST/DB_PORT/DB_USER/DB_PASSWORD/DB_NAME` | localhost/3306/root/000/X | 与 Node `db.js` 默认一致 |
| `SMTP_*` | — | 邮件验证码 |
| `DEEPSEEK_API_KEY/URL`、`NSCC_API_KEY/URL` | — | 两个模型 |
| `UPLOADS_DIR` | `../data/uploads` | 数据集目录（复用历史文件） |
| `HISTORY_DIR` | `../data/storage/history` | 生成记录 JSON |
| `CHARTS_DIR` | `../data/storage/generated_charts` | 编译产物 PDF |
| `RELATIVE_BASE` | `..` | `generation_path` 相对路径解析基准 |
| `XELATEX` / `LATEX_TIMEOUT_MS` | `xelatex` / 30000 | 编译器与超时 |
| `LATEX_MAX_CONCURRENCY` | `2` | XeLaTeX 并发编译上限（批次2/G2，`Semaphore` 限流） |

## 6. 包结构

```
com.pg.pgfplots
├── common/     Result / BusinessException / GlobalExceptionHandler
├── config/     SecurityConfig / MybatisPlusConfig / WebConfig / AppProperties
├── security/   JwtTokenProvider / JwtAuthenticationFilter / LoginUser / SecurityUtils
├── entity/     11 张表实体
├── mapper/     MyBatis-Plus Mapper（+ resources/mapper/*.xml）
├── dto/        请求/响应 DTO（字段名保持 Node 契约）
├── client/     LlmClient（Qwen / DeepSeek）
├── util/       FileStorage / FileContentReader / LatexCompiler / ChartCodeExtractor / PromptTemplates / SystemLogWriter / TimeFormat
├── service/    13 个业务服务
└── controller/ 13 个控制器
```

## 7. 如何验证功能是否完成

### 7.1 一键自动验证（推荐）

```bat
cd hello\spring-backend
build.cmd        :: 先确保能构建
verify.cmd       :: 自动启动后端 + 回归全部接口，输出 PASS/FAIL
```

`verify.js` 覆盖：鉴权（401/403/管理员登录）、管理后台（static / users / notices / log 统计，含 **responseTime 真实聚合、errorTypes 失败分类、responseTimeSeries 按日耗时序列**三条断言）、用户侧（notice / history / conversations / feedback / validate）、**数据集上传·列表·改名·下载·删除**、**XeLaTeX 异步编译（提交 task_id → 轮询终态 → duration_ms）+ PDF 鉴权流式返回**。

**最近一次结果（2026-09-14）：`PASS=31  FAIL=0  WARN=0`，全部通过。**

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
| UTF-8 | 中文正常（`curl.exe` 原始字节校验） |

## 8. 退役 Node 后端（✅ 已于 2026-09-13 执行完成）

> **状态：Node/Express 后端代码已全部删除。** 其数据目录 `backend/` 已于同日改名为 `data/`，现为共享运行时数据目录，仅含 `.env`、`uploads/`、`storage/`，**Java 仍在读取，请勿删除**。`conversations` 建表 DDL 已归档至 `hello/migrations/create_conversations_tables.sql`。

### 8.1 与 Node 共存/冲突说明（已核对，无冲突）

| 项 | 说明 |
|---|---|
| 数据库 | Java 通过 `..\backend\.env` 读取同一份 `DB_*`，连的是**同一个库 `X`**，**无需迁移数据、无需改 schema** |
| `data/uploads` | Java 默认 `UPLOADS_DIR=../data/uploads`；历史文件与新增上传文件均在此目录，29 个历史数据集照常可读 |
| `data/storage` | Java 默认 `HISTORY_DIR=../data/storage/history`、`CHARTS_DIR=../data/storage/generated_charts`；`generation_path` 以 `RELATIVE_BASE=..` 解析，**历史 PDF 可直接读取**（DB 前缀已同步改为 `data\`） |
| 端口 | 默认 **3000**；退役后仅剩 Java 一个后端，前端 `API_BASE_URL` 无需修改 |

启动日志会打印解析后的绝对路径，便于确认（见 §7.4）。

### 8.2 已执行的退役步骤（存档）

1. ✅ 确认前端 `API_BASE_URL` 指向 Java 后端且联调通过（`verify.cmd` 基线 `PASS=27 FAIL=0`）。
2. ✅ 保留 `backend/.env`、`backend/uploads`、`backend/storage`（Java 复用）。
3. ✅ 归档 `backend/migrations/001_conversations.js` 的建表 DDL 为 `hello/migrations/create_conversations_tables.sql`。
4. ✅ 删除 `backend/` 下全部 Node 代码：`app.js`、`db.js`、`createAdmin.js`、`routes/`、`middleware/`、`services/`、`utils/`、`migrations/001_conversations.js`、`package.json`、`package-lock.json`、`yarn.lock`、`.env.example`、`node_modules`。
5. ✅ 同步更新 `hello/README.md`、`docs/architecture.md`、`docs/development.md`、`docs/deployment.md`、`.github/copilot-instructions.md` 的后端描述。
