# PGFPlotsGenerator 系统架构与设计文档

> **文档说明**：本文档为 **PGFPlotsGenerator 智能图表生成系统** 的架构视图文档，聚焦**系统架构、核心模块与关键数据流**。内容基于 `2026-09-05` 对 `hello/` 目录下实际源码的逐文件核实，所有结论均可追溯到代码行号。与 `README.md`（API/部署细节）互补，两者不一致处见文末「与 README 差异清单」。

## 1. 系统概述

PGFPlotsGenerator 是一个「AI 图表生成系统」：普通用户用自然语言描述图表需求（可附带 Excel/CSV/文本数据集），后端调用大语言模型生成 LaTeX/PGFPlots 代码，再通过 XeLaTeX 编译为 PDF 供在线预览与下载；全过程持久化到 MySQL 与本地文件系统，支持历史回溯、多轮对话、数据文件管理与反馈通知。管理员通过独立后台管理用户、通知与系统监控。

- **产品定位**：自然语言 → 图表 PDF 的一站式生成工具
- **用户角色**：普通用户（生成/历史/数据集/对话/反馈/通知/个人信息）与管理员（用户、通知、反馈回复、系统日志/API 统计）
- **核心链路**：`自然语言(±数据集) → AI 生成 LaTeX → XeLaTeX 编译 → PDF 预览/下载`

## 2. 总体架构

系统为**前后端分离的经典分层架构**：浏览器端 Vue 3 SPA（`hello/src`），服务端 Spring Boot 应用（`hello/spring-backend`；原 Node/Express 版已于 2026-09-13 退役删除），数据层为 MySQL（库名 `X`）+ 本地文件系统，外部依赖为两家 LLM 服务、XeLaTeX 编译器与 SMTP 邮件服务。

### 2.1 逻辑架构

```mermaid
flowchart TD
    subgraph Browser["浏览器（Vue 3 SPA，hello/src）"]
        UI_USER["用户侧<br/>CommonNavbar + 主内容区<br/>TheAuth(登录·注册)"]
        UI_ADMIN["管理员侧<br/>AdminNavbar / AdminSidebar / AdminLogin"]
        V_USER["用户视图<br/>ChartGenerator · MyHistory · DataUpload<br/>MyFeedback · MyNotice · ChangeInformation"]
        V_ADMIN["管理员视图<br/>AdminFeedback · AdminLog · AdminNotice · AdminUser"]
        ROUTER["vue-router 14 条平级路由"]
        VUEX["Vuex（currentUser / unreadCount）"]
        UI_USER --> ROUTER
        UI_ADMIN --> ROUTER
        ROUTER --> V_USER
        ROUTER --> V_ADMIN
        VUEX --- UI_USER
    end

    subgraph JAVA["Spring Boot 服务（hello/spring-backend）"]
        AUTH["Spring Security<br/>JwtAuthenticationFilter（角色查库装配）"]
        subgraph API["13 个控制器（路径与退役 Express 版一致）"]
            R1["/api/auth"]
            R2["/api/chat"]
            R3["/api/feedback"]
            R4["/api/verification"]
            R5["/api/datasets"]
            R6["/api/compile"]
            R7["/api/history"]
            R8["/api/notice"]
            R9["/api/conversations"]
            R10["/api/admin/notices"]
            R11["/api/admin/users"]
            R12["/api/admin/log"]
            R13["/api/admin/static"]
        end
        SVC["支撑件<br/>util/SystemLogWriter · service/VerificationService"]
        DB["MyBatis-Plus Mapper + HikariCP"]
    end

    subgraph Data["数据与外部依赖"]
        MYSQL[("MySQL 库 X<br/>11 张表")]
        FS[("文件系统<br/>data/uploads · data/storage")]
        TEX["XeLaTeX 编译器<br/>(ProcessBuilder, 30s)"]
        LLM["LLM<br/>DeepSeek / Qwen3.5(NSCC·RestClient)"]
        SMTP["SMTP 邮件服务<br/>(JavaMailSender)"]
    end

    Browser -- "HTTP/JSON + Bearer JWT<br/>(API_BASE_URL=http://localhost:3000)" --> JAVA
    API --> AUTH
    JAVA --> DB --> MYSQL
    JAVA --> FS
    R2 -- "LlmClient（RestClient）" --> LLM
    R6 -- "xelatex -interaction=nonstopmode" --> TEX
    R4 -- "JavaMailSender" --> SMTP
    JAVA -- "读写 storage/uploads（服务端内部，无公开静态托管）" --> FS
    R5 -- "MultipartFile 上传" --> FS
```

### 2.2 运行/部署视角

```mermaid
flowchart LR
    subgraph Dev["开发机"]
        FE["npm run serve<br/>(vue-cli-service, 8080)"]
        BE["cd spring-backend && run.cmd<br/>(:3000)"]
        MYSQL_LOCAL["MySQL localhost/root/000/X<br/>(读 ../backend/.env / 默认)"]
        TEX_LOCAL["TeX Live：xelatex + SimSun/Times New Roman"]
    end
    FE -- "API_BASE_URL 代理目标 :3000" --> BE
    BE --> MYSQL_LOCAL
    BE --> TEX_LOCAL
    FE -- "GET /api/compile/:id/pdf（Bearer 鉴权，Blob 预览）" --> BE
    BE -- "SMTP QQ / DeepSeek / NSCC-Qwen" --> EXT["互联网服务"]
```

## 3. 核心模块说明

### 3.1 模块总览

| 模块 | 后端入口 | 前端对应 | 职责与关键实现 |
|---|---|---|---|
| 认证 | `controller/AuthController` → `service/AuthService` | LoginForm / RegisterForm / AdminLogin / ChangeInformation | 注册、用户/管理员登录、改密码/用户名/邮箱、token 校验；BCrypt 哈希 + JWT |
| AI 生成 | `controller/ChatController` → `service/ChatService` | ChartGenerator | 接收自然语言+数据集 → 拼提示词 → 调 DeepSeek/Qwen → 提取代码 → 落库 |
| 编译 | `controller/CompileController` → `service/CompileService` | ChartGenerator（生成 PDF 按钮） | 读库内代码 → 包中文文档 → XeLaTeX → 移动 PDF、更新路径 |
| 历史 | `controller/HistoryController` → `service/HistoryService` | MyHistory | 列表/详情/删除/统计/CSV 导出 |
| 数据集 | `controller/DatasetController` → `service/DatasetService` | DataUpload / ChartGenerator | 上传(≤100MB)/列表/改名/删除/下载 |
| 对话持久化 | `controller/ConversationController` → `service/ConversationService` | ChartGenerator（会话侧栏） | 会话 CRUD + 消息读取；chat 生成时联动写消息 |
| 反馈 | `controller/FeedbackController` → `service/FeedbackService` | MyFeedback / AdminFeedback | 用户提交查看；管理员列表/详情/回复(联动写通知)/删除 |
| 通知 | `controller/NoticeController` → `service/NoticeService` | MyNotice / CommonNavbar | 系统通知+反馈回复统一列表；单条/全部已读；未读计数 |
| 邮件验证码 | `controller/VerificationController` → `service/VerificationService` | RegisterForm / ChangeInformation | 发送/校验注册·改邮箱验证码 |
| 管理员-用户 | `controller/AdminUserController` | AdminUser | 用户列表/重置密码(666666)/级联删除/统计 |
| 管理员-通知 | `controller/AdminNoticeController` | AdminNotice | 通知 CRUD（广播）+ 已读统计 |
| 管理员-日志 | `controller/AdminLogController` | AdminLog | API 统计/系统日志/健康概览/表状态 |
| 管理员-概览 | `controller/AdminStaticController` | Admin（Admin.vue 首页） | 全库计数（users/files/generations/feedback） |

> 后端入口统一由 Spring Security 过滤器链拦截（`SecurityConfig`；`/api/admin/**` 要求 ADMIN 角色）。业务接口除验证码外均需 `Authorization: Bearer <token>`。

### 3.2 各模块详细说明

#### 3.2.1 认证（auth）

- 入口：`controller/AuthController` → `service/AuthService`
- 接口：`POST /admin/login`、`POST /register`、`POST /login`、`POST /change-password`、`POST /change-username`、`POST /change-email`、`GET /validate`（路径与退役 Express 版一致）
- 关键逻辑：
  - 密码统一 BCrypt 哈希存储与校验（`BCryptPasswordEncoder`）
  - 密码格式校验：**仅字母与数字、长度 6–16 位**（注册/改密共用，`AuthService` 内实现）
  - JWT 签发：管理员 token 7 天、用户 token 24h（`security/JwtTokenProvider`，claims 与退役版一致）
- 鉴权校验由 `security/JwtAuthenticationFilter` 完成：解析 `Authorization: Bearer` → 验签（密钥 `JWT_SECRET`）→ 按 userId 查 `users` 表 → 装配登录用户（**不信任 JWT payload 中的角色声明**）

> 角色判断注意：管理员接口统一由 `SecurityConfig` 对 `/api/admin/**` 要求 ADMIN 角色（查库装配）；feedback 管理员接口另加 `@PreAuthorize` 双校验，均不信任 JWT payload 中的角色声明。

#### 3.2.2 AI 生成（chat）

- 入口：`controller/ChatController` → `service/ChatService`，单路由 `POST /api/chat`（需认证）
- 处理流水线：
  1. 数据集拼接：从 `data_file` 按 user_id 隔离查询，逐一用 `util/FileContentReader`（支持 xlsx/csv/文本，上限 100MB）把数据拼入 system 提示词；无数据集时追加提示「用已知公开统计数据出图」
  2. 按 `model` 分派（均经 `client/LlmClient`，Spring 6 RestClient）：
     - `model === 'qwen'`：指向 `NSCC_API_URL`（湖大超算 MaaS），模型 `Qwen3.5`，`max_tokens: 8192`、关闭 thinking
     - 否则（deepseek）：调 `DEEPSEEK_API_URL`，模型 `deepseek-v4-flash`，`max_tokens: 4096`，超时 30s
  3. **空回复兜底**：`content` 为 null/空/非字符串时打印原始响应、写系统日志并返回 `502「AI 返回内容为空」`
  4. `util/ChartCodeExtractor`：优先匹配「```latex / ```tex 代码块围栏」，兜底裸 `\begin{tikzpicture}...\end{tikzpicture}`（防截断围栏不闭合）；返回值即入库代码
  5. 生成成功后：INSERT `generation_history` → 写 `storage/history/{userId}/{historyId}.json`（含 ai_response 全文）→ INSERT `api_log`(success)
  6. 若请求带 `conversation_id`：写 user/assistant 两条 `conversation_messages`（assistant 消息带 chart_code/history_id），首条自动回填标题
  7. 失败路径：模型异常时记录 `api_log`(failed, call_error 截断 500) 并按错误类型返回
- 返回结构 `{ success, data: { reply, chart_code, usage, dataset_count, history_id } }`

#### 3.2.3 编译（compile）

- 入口：`controller/CompileController` → `service/CompileTaskService`（批次2/G1 异步任务）→ `service/CompileService`
- 接口：`POST /:history_id`（提交编译任务，**批次2/G1 起立即返回 `{task_id, status:"queued", history_id}`，不再同步阻塞**）、`GET /task/:task_id`（轮询终态：success 含 `pdf_path/file_size/duration_ms`，failed 含 `error`；按 userId 校验归属，越权或服务重启后丢失统一 404）、`GET /:history_id/pdf`（鉴权流式返回 PDF 文件），均需认证
- 关键事实：
  - **POST 只读取库内 `generation_history.generation_code`**，**不接受请求体 code 覆盖**；历史存在性与归属校验在工作线程内完成
  - **异步执行（G1）**：内存任务表（`queued→running→success/failed`，重启丢失为已知取舍）+ `compileTaskExecutor`（core2/max4/queue100）；队列满**不静默丢弃**，任务标记 failed 并返回 503
  - **并发上限（G2）**：`Semaphore` 限流真实 XeLaTeX 进程并发 = `app.latex.max-concurrency`（默认 2），等待时长计入任务 `duration_ms`
  - 若代码含完整 `document` 结构则抽取文档体并清理 documentclass/usepackage
  - 中文文档：`standalone` 文档类 + `pgfplots/compat=1.18` + `xeCJK`，**中文字体 SimSun、西文 Times New Roman**
  - `util/LatexCompiler`：`xelatex -interaction=nonstopmode`，超时 **30s**；以 PDF 是否存在判成败；代码长度上限由 `app.latex.max-code-length` 驱动
  - 成功：PDF 移动至 `storage/generated_charts/user{userId}/hist{historyId}.pdf`，更新 `generation_path`（相对路径）；失败必清理临时目录
- PDF 访问：已移除 `/storage` 无鉴权静态托管；前端通过 `GET /api/compile/:id/pdf` 携带 JWT，服务端按 `user_id` 校验归属后流式返回，前端 `fetch → blob → objectURL` 预览（`src/utils/pdf.js`）

#### 3.2.4 历史（history）

- 接口：`GET /`（列表+分页/搜索）、`GET /:id`、`DELETE /:id`、`GET /stats/summary`、`GET /export/csv`
- 删除使用**事务**：先删 `api_log` 子表再删 `generation_history`（`HistoryService` `@Transactional`）；所有查询均带 `user_id = ?` 隔离

#### 3.2.5 数据集（datasets）

- 接口：`GET /`、`POST /`（`MultipartFile` 上传）、`POST /:id/update`、`DELETE /:id`、`GET /download/:id`
- 上传落盘 `data/uploads/`（`util/FileStorage`），**≤100MB**
- 文件预览无独立后端接口：`util/FileContentReader` 在 chat 生成时按 data_ids 解析文件内容，前端从数据集列表直接拿到文件元信息

#### 3.2.6 对话持久化（conversations）

- 接口：`GET /`（含 message_count/last_message）、`POST /`（默认标题「新对话」）、`PUT /:id`（重命名）、`DELETE /:id`（**事务先删消息再删会话**）、`GET /:id/messages`（按 message_id 正序返回 content/chart_code/history_id/selected_files）
- 写入方：`ChatService` 在生成成功后写入一轮 user+assistant 消息
- 表：`conversations` / `conversation_messages`（无外键，关联由代码事务维护；建表 DDL 归档于 `migrations/create_conversations_tables.sql`）

#### 3.2.7 反馈（feedback）

- 用户接口：`POST /`（type ∈ suggestion/ui/bug/other，content ≤100）、`GET /user/my-feedbacks`（含「已回复/待回复」状态）
- 管理员接口：`GET /`（筛选 type/日期）、`GET /:id`、`PUT /:id/reply`、`DELETE /:id`
- **回复→通知联动**：更新 `feedback.answer` 后，INSERT 一条定向 `notice`（target_user_id=反馈者、feedback_id 关联、reply=回复内容；`feedback_id` 唯一索引保证幂等）

#### 3.2.8 通知（notice）

- 接口：`GET /`（返回列表+unreadCount）、`POST /read/:id`（写 `notice_read`）、`POST /read-all`、`GET /unread-count`
- 通知来源两类，统一走 `notice` + `notice_read`：
  - 系统广播：`AdminNoticeController` 创建，`target_user_id = NULL`
  - 反馈回复：`FeedbackService` 回复时写入，`target_user_id = 反馈者`
- 前端未读数红点由 `store/index.js` 的 `fetchUnreadCount` 拉取

#### 3.2.9 邮件验证码（verification）

- 接口：`POST /send-register-code`、`POST /verify-register-code`，**均免 token**
- `service/VerificationService`：生成 6 位随机码、SMTP 发送（JavaMailSender）、落 `email_verification_codes`（10 分钟过期、一次性）
- 注册与改邮箱前校验（`AuthService` register / change-email）

#### 3.2.10 管理员后台（Admin*，SecurityConfig 统一要求 ADMIN 角色）

| 模块 | 主要接口 | 备注 |
|---|---|---|
| AdminUserController | `GET /`、`PATCH /:id/reset-password`（重置为 `666666`，BCrypt 哈希）、`DELETE /:id`（级联删除关联数据）、`GET /statistics/overview` | — |
| AdminNoticeController | `GET /`、`GET /:id`、`POST /`、`PUT /:id`、`DELETE /:id`、`DELETE /`（批量）、`GET /statistics/overview` | 广播通知 |
| AdminLogController | `GET /api-stats`、`GET /system-logs`、`GET /health-overview`、`POST /add-test-log`、`GET /tables-status` | — |
| AdminStaticController | `GET /`（全库计数） | — |

### 3.3 支撑件

| 文件 | 职责 |
|---|---|
| `security/JwtAuthenticationFilter` + `config/SecurityConfig` | JWT 过滤器与安全配置（角色查库装配，见 3.2.1） |
| `util/SystemLogWriter` | `writeSystemLog(status, message)` 写 `system_log`（error 截断 500 字；自身失败仅 console，不抛异常） |
| `service/VerificationService` | 验证码生成/发信/落库 |
| `common/Result` + `common/GlobalExceptionHandler` | 统一响应 `{success,data,message}` 与业务异常转换 |

### 3.4 前端路由与视图（前后端映射）

路由定义于 `src/router/index.js`，**14 条平级路由、无嵌套布局、无全局路由守卫**（无 `beforeEach`）：

| 路由 | 组件 | 后端依赖 | 说明 |
|---|---|---|---|
| `/` | — | — | 动态重定向：有 `adminUser` → `/admin`，否则 `/chart-generator` |
| `/chart-generator` | ChartGenerator | `/api/chat` `/api/compile/:id` `/api/conversations*` `/api/datasets` | 核心对话页：模型切换（qwen 默认/deepseek）、选数据集、生成→编译→PDF 预览 |
| `/history` | MyHistory | `/api/history*` | 历史列表/详情/删除/导出 CSV |
| `/data-upload` | DataUpload | `/api/datasets` | 数据集上传/管理 |
| `/feedback` | MyFeedback | `/api/feedback` | 我的反馈 |
| `/MyNotice` | MyNotice | `/api/notice` | 我的通知 |
| `/change-information` | ChangeInformation | `/api/auth` + `/api/verification` | 改密码/用户名/邮箱 |
| `/login` / `/register` | LoginForm / RegisterForm | `/api/auth` + `/api/verification` | 认证（TheAuth 容器切换） |
| `/admin` | Admin.vue | `/api/admin/static` | 管理员概览首页 |
| `/admin/user` | AdminUser | `/api/admin/users` | 用户管理 |
| `/admin/notice` | AdminNotice | `/api/admin/notices` | 通知管理 |
| `/admin/feedback` | AdminFeedback | `/api/feedback`（管理员） | 反馈管理 |
| `/admin/log` | AdminLog | `/api/admin/log` | 系统监控 |

- 布局：`App.vue` 依状态渲染——未登录显示 `TheAuth`；普通用户显示 `CommonNavbar` + 主区；管理员显示 `AdminNavbar` + `AdminSidebar` + 路由视图（`App.vue:22-65`）；管理员认证状态存 `localStorage.adminUser/adminToken`，非 Vuex
- 状态管理：`store/index.js`（Vuex）——`currentUser/isAuthenticated/unreadCount`；axios **无统一实例与响应拦截器**，各组件自行拼 `Authorization` 头（如 `ChartGenerator.vue:883-890`）；`API_BASE_URL` 定义于 `src/config.js:2`（默认 `http://localhost:3000`）
- 未读数：进入 MyNotice 时经 store action 拉 `/api/notice/unread-count`（`App.vue:107-110, 222-224`）

## 4. 关键数据流

### 4.1 生成 → 编译 → 预览 主链路

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant CG as ChartGenerator.vue
    participant CHAT as POST /api/chat
    participant LLM as LLM(DeepSeek/Qwen)
    participant DB as MySQL + storage/history
    participant COM as POST /api/compile/:id
    participant TEX as XeLaTeX
    participant FS as storage/generated_charts

    U->>CG: 输入自然语言 + 选择数据集(可选) + 选模型
    CG->>CHAT: {message, data_ids?, model, conversation_id?} + Bearer JWT
    CHAT->>CHAT: buildMessagesWithDataset：查 data_file、readFileContent 拼提示词
    CHAT->>LLM: 调模型（LlmClient：qwen→NSCC；其他→DeepSeek）
    alt content 为空/null
        CHAT-->>CG: 502「AI 返回内容为空」（写 system_log）
    else 正常返回
        LLM-->>CHAT: aiReply + usage
        CHAT->>CHAT: extractChartCode 提取 LaTeX；saveGenerationHistory 落库 + storage json + api_log(success)
        CHAT->>CHAT: persistConversation（若传 conversation_id）
        CHAT-->>CG: {reply, chart_code, history_id}
    end
    CG->>COM: POST /api/compile/{history_id}
    COM->>COM: CompileTaskService.submit → compileTaskExecutor 入队
    COM-->>CG: {task_id, status:"queued"}（立即返回，不再阻塞 30s）
    loop 每 1.5s 轮询（上限 90s）
        CG->>COM: GET /api/compile/task/{task_id}
    end
    COM->>COM: 取 Semaphore 许可（并发 ≤ app.latex.max-concurrency）
    COM->>DB: 查 generation_code（仅库内代码，不接受 body.code）
    COM->>COM: preprocess + createChineseLatexDocument(standalone+xeCJK)
    COM->>TEX: xelatex -interaction=nonstopmode（30s 超时）
    alt 编译失败
        TEX-->>COM: stderr
        COM-->>CG: task status=failed + error（截断 500）
    else 成功
        COM->>FS: hist{id}.pdf → user{uid}/ 目录，更新 generation_path
        COM-->>CG: task status=success + pdf_path/file_size/duration_ms
        CG->>CG: fetch GET /api/compile/:id/pdf（Bearer）→ blob objectURL 预览
    end
```

### 4.2 鉴权数据流

```mermaid
flowchart LR
    A["登录/注册（AuthService）<br/>BCrypt 校验/哈希"] --> B["签发 JWT<br/>用户 24h / 管理员 7d<br/>(JWT_SECRET)"]
    B --> C["前端存 localStorage/sessionStorage<br/>token + user；管理员 adminToken"]
    C --> D["请求头<br/>Authorization: Bearer <token>"]
    D --> E["JwtAuthenticationFilter"]
    E --> F["验签 → 查 users → 装配角色<br/>(不信任 JWT 中角色声明)"]
    F --> G["业务路由按 user_id=? 隔离数据"]
    E -. "管理员接口" .-> H["SecurityConfig 要求 ADMIN<br/>(查库装配角色)"]
    E -. "feedback 管理接口" .-> H["@PreAuthorize 双校验"]
```

### 4.3 对话持久化数据流

```mermaid
flowchart LR
    A["ChartGenerator<br/>发送消息(带 conversation_id)"] --> B["POST /api/chat"]
    B --> C["生成成功"]
    C --> D["persistConversation<br/>(chat.js:284-316)"]
    D --> E["INSERT conversation_messages<br/>user + assistant(chart_code/history_id)"]
    E --> F["首条消息自动回填标题<br/>(用户输入前 20 字)"]
    G["切换/新建会话<br/>(ConversationService)"] --> H["GET /:id/messages<br/>按序恢复聊天记录"]
    H --> A
```

### 4.4 反馈 → 通知联动

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 管理员(AdminFeedback.vue)
    participant FB as PUT /api/feedback/:id/reply
    participant N as notice 表
    participant U as 用户(MyNotice.vue)

    Admin->>FB: 回复 {answer≤500}
    FB->>FB: 校验 + UPDATE feedback SET answer
    FB->>N: INSERT notice(target_user_id=反馈者, feedback_id, reply)<br/>(feedback_id 唯一索引幂等)
    U->>U: 导航栏红点（/api/notice/unread-count）
    U->>N: GET /api/notice → 看到 type=feedback 通知
    U->>N: POST /read/:id → 写 notice_read
```

### 4.5 文件路径约定

| 路径 | 内容 | 写入方 |
|---|---|---|
| `data/uploads/` | 数据集原始文件（MultipartFile 落盘） | `util/FileStorage` |
| `data/storage/history/{userId}/{historyId}.json` | 完整对话/生成记录（含 ai_response） | `service/ChatService` |
| `data/storage/generated_charts/user{userId}/hist{historyId}.pdf` | 编译产物（`generation_path` 相对项目根） | `service/CompileService` |
| `GET /api/compile/:id/pdf` | PDF 鉴权流式返回（按 user_id 归属校验） | `controller/CompileController` |

## 5. 数据存储设计

### 5.1 ER 图

```mermaid
erDiagram
    users ||--o{ data_file : "data_file.user_id FK"
    users ||--o{ generation_history : "generation_history.user_id FK"
    data_file ||--o{ generation_history : "data_id FK (SET NULL)"
    generation_history ||--o{ api_log : "history_id FK"
    users ||--o{ feedback : "user_id FK"
    users ||--o{ notice : "admin_id FK"
    notice ||--o{ notice_read : "notice_id FK"
    users ||--o{ notice_read : "user_id FK"
    users ||--o{ conversations : "user_id (无FK)"
    conversations ||--o{ conversation_messages : "conversation_id (无FK)"
    generation_history ||--o{ conversation_messages : "history_id (无FK)"
    feedback ||--o| notice : "feedback_id 唯一索引"

    users {
        int user_id PK
        varchar username
        varchar email
        varchar password "bcrypt"
        enum role "user|admin"
        datetime register_time
    }
    data_file {
        int data_id PK
        int user_id FK
        varchar data_name
        int data_size
        text description
        varchar file_path
        varchar file_name
    }
    generation_history {
        int history_id PK
        int user_id FK
        int data_id FK "nullable"
        text generation_description
        text generation_code "LaTeX"
        varchar generation_path
        datetime generation_time
    }
    api_log {
        int call_id PK
        int user_id FK
        int history_id FK
        enum call_status "success|failed"
        text call_error
    }
    feedback {
        int feedback_id PK
        int user_id FK
        enum type "suggestion|ui|bug|other"
        text content
        text answer
    }
    notice {
        int notice_id PK
        varchar title
        text content
        int admin_id FK
        int target_user_id "定向用户，NULL=广播"
        int feedback_id "UNIQUE 来源反馈"
        text reply
    }
    notice_read {
        int id PK
        int user_id FK
        int notice_id FK
        datetime read_time
    }
    system_log {
        int sys_id PK
        enum system_status "normal|warning|error"
        text error
    }
    email_verification_codes {
        int id PK
        varchar email
        varchar code "6位"
        datetime expires_at
    }
    conversations {
        int conversation_id PK
        int user_id
        varchar title
        datetime updated_at
    }
    conversation_messages {
        int message_id PK
        int conversation_id
        varchar role "user|assistant"
        text content
        text chart_code
        int history_id
        text selected_files
    }
```

### 5.2 表与建表来源

库名 `X`，共 **11 张表**：

| 表 | 关键列 | 来源 | 备注 |
|---|---|---|---|
| `users` | user_id/username/email/password/role | `1.sql:5-12` | 预置管理员 `admin123/666666`（`1.sql:14-20`） |
| `data_file` | data_id/user_id/data_name/data_size/file_path/... | `1.sql:22-34` | user_id FK ON DELETE CASCADE |
| `generation_history` | history_id/user_id/data_id/generation_code/generation_path | `1.sql:36-46` | data_id FK SET NULL |
| `api_log` | call_id/user_id/history_id/call_status/call_error + **prompt_version**（批次1/A2）+ **duration_ms**（批次2/O1）+ **error_type**（批次3/E2） | `1.sql:48-57` + `migrations/alter_api_log_prompt_version.sql` + `migrations/alter_api_log_duration_ms.sql` + `migrations/alter_api_log_error_type.sql` | 关联历史；批次3 起语义为「**AI 链路失败事件日志**」（含编译失败）；duration_ms 供聚合 avg/P95（只统计生成链路，编译耗时不混算），error_type 供失败归类计数 |
| `feedback` | feedback_id/user_id/type/content/answer/answer_time | `1.sql:59-68` | — |
| `system_log` | sys_id/system_status/error | `1.sql:70-75` | writeSystemLog 写入 |
| `email_verification_codes` | email/code/expires_at | `1.sql:77-85` | 10 分钟过期 |
| `notice` | notice_id/title/content/admin_id + **target_user_id/feedback_id/feedback_time/feedback_type/reply** | `1.sql:87-95` + `migrations/add_notice_feedback_columns.sql` | 后 5 列迁移追加；`feedback_id` 唯一索引 |
| `notice_read` | id/user_id/notice_id/read_time | `migrations/create_notice_read_table.sql:8-17` | user+notice 唯一索引 |
| `conversations` | conversation_id/user_id/title/created_at/updated_at | `migrations/create_conversations_tables.sql`（自 Node 迁移脚本归档） | **无外键**，事务维护 |
| `conversation_messages` | message_id/conversation_id/user_id/role/content/chart_code/history_id/selected_files | `migrations/create_conversations_tables.sql`（自 Node 迁移脚本归档） | **无外键** |

> 实现事实：基础表部分外键在 DDL 中声明；`conversations`/`conversation_messages` 之间及与历史/用户之间**不设外键**，会话删除由 `ConversationService` 在事务内先删消息再删会话。

### 5.3 建表/迁移执行顺序

```bash
mysql -u root -p000 X < 1.sql
mysql -u root -p000 X < migrations/add_notice_feedback_columns.sql
mysql -u root -p000 X < migrations/create_notice_read_table.sql
mysql -u root -p000 X < migrations/create_conversations_tables.sql
mysql -u root -p000 X < migrations/alter_api_log_duration_ms.sql
mysql -u root -p000 X < migrations/alter_api_log_error_type.sql
```

## 6. 技术栈清单

| 层 | 技术 | 依据 |
|---|---|---|
| 前端框架 | Vue `^3.2.13`（Composition API 与 Option API 混用）+ Vue Router `^4.6.3` + Vuex `^4.0.2` | `hello/package.json` |
| UI | Element Plus `^2.11.9` + `@element-plus/icons-vue`；监控页 ECharts `^6.0.0` | 同上 |
| 构建 | **Vue CLI 5**（`vue-cli-service serve/build`，非 Vite） | `hello/package.json` scripts、`vue.config.js` |
| HTTP | axios（组件内直调，无统一封装）；部分 fetch | `ChartGenerator.vue:434` 等 |
| 后端框架 | Spring Boot `3.2.12` + MyBatis-Plus `3.5.5`（`spring-backend/`，Java 17） | `spring-backend/pom.xml` |
| 数据库 | MySQL（HikariCP 连接池） | `application.yml` |
| LLM 调用 | Spring 6 `RestClient`（Qwen3.5/NSCC、DeepSeek `deepseek-v4-flash`） | `client/LlmClient.java` |
| 编译 | ProcessBuilder 调 **xelatex**（TeX Live + SimSun/Times New Roman 字体，30s 超时） | `util/LatexCompiler.java` |
| 邮件 | spring-boot-starter-mail（QQ SMTP） | `service/VerificationService.java` |
| 上传/解析 | Spring `MultipartFile`（≤100MB）+ Apache POI 5.2.5（xlsx） | `controller/DatasetController.java`、`util/FileContentReader.java` |
| 安全 | Spring Security 无状态 JWT（jjwt 0.12.6）+ BCrypt | `security/` |

> 前后端位于同一工程 `hello/`：根 `package.json` 为前端依赖（已移除仅供 Node 后端使用的 bcryptjs/cors/express/jsonwebtoken/multer 五项）；后端依赖见 `spring-backend/pom.xml`。

## 7. 与 README.md 差异清单（2026-09-05 复核）

> README v2.0 基准日期 2026-07-16。以下差异以代码为准。

| # | 主题 | README/plan 声称 | 代码实证（本文件基准） |
|---|---|---|---|
| 1 | 后端路由数量 | 多处称「**14 个**路由前缀/模块」（README §1/§5/§7/§11#8） | **实际 13 个前缀**（app.js:27-39），routes/ 下 13 个 js；无 `/api/user` 等额外前缀 |
| 2 | `notice` 表 `is_read` 字段 | §8 表未提 | `1.sql:92` 存在 `is_read BOOLEAN`（旧机制字段），迁移引入 `notice_read` 后实际按每用户已读记录工作，README 未明确该字段废弃状态 |
| 3 | plan「后端待实现」· 改码重编译 | 期望 `POST /api/compile` 接受 `{history_id, code}` 覆盖 | 未实现：compile.js 仅读库内 `generation_code`（:132）；前端编译请求 body 为空（`ChartGenerator.vue:986`） |
| 4 | plan「后端待实现」· AI 修复 | 期望 `POST /api/chat/fix` | 未实现：chat.js 无该路由（仅 `POST /`）；前端无对应调用 |
| 5 | plan「前端已做」改码重编译 | 声称前端「编辑代码→重编译」已做 | ChartGenerator 消息代码区未见编辑入口；编译仅对 `message.historyId` 发起（:986） |
| 6 | 空气泡修复 | plan.md 记为待实施 | **已落地**：chat.js:372-384 / :421-429 对空 content 返回 502 + 写系统日志；前端 `ChartGenerator.vue:932-935` 渲染明确错误消息 |
| 7 | 无数据集出图 | plan 契约 | **已落地**：buildMessagesWithDataset 无数据集分支提示用公开数据出图（chat.js:121-124、system 提示词 :113） |
| 8 | `readFileContent` 归属 | README §4 数据集行文字「readFileContent 在此文件内定义」 | 实际仅定义于 `chat.js:16-77`，datasets.js 无该函数；文件预览无后端独立接口 |
| 9 | 前端路由数 | （README 未列路由明细） | router 实际 **14 条记录**（含 `/` redirect），13 个页面组件；无路由守卫，鉴权由 App.vue 条件渲染 + 后端 JWT 兜底 |
| 10 | 依赖声明 | README 称 cors/multer 未声明于 backend/package.json | 仍属实：backend/package.json 无 cors/multer（运行时依赖已装 node_modules），部署需注意 |

## 8. 安全与可运维性提示（2026-09-07 已加固，下述为当前状态与运维注意）

- ✅ `/api/admin/*` 四个模块（AdminUser/AdminNotice/AdminLog/AdminStatic）由 `SecurityConfig` 统一要求 ADMIN 角色（查库装配，不信任 JWT 声明）；feedback 管理员接口另加 `@PreAuthorize` 双校验；均为服务端强制鉴权，不再依赖前端隐藏
- ✅ PDF 已移除 `/storage` 静态托管与 `/storage` 反代，改 `GET /api/compile/:id/pdf` 按 user_id 归属校验后流式返回（history JSON 目录不再可被 HTTP 访问）
- ✅ LaTeX 编译前拦截 `\write18`/`\input`/`\include`/`\usepackage` 等危险序列并限制长度
- ✅ DB 凭据读 `../backend/.env`（缺省回退本地默认值）；`JWT_SECRET` 缺失启动即失败（fail-fast）
- ✅ 密码规则收紧为仅字母数字 6–16 位（`AuthService` 校验），预置管理员 `admin123/666666` 与重置密码均满足
- ⚠️ 运维注意：`backend/.env` 明文含 SMTP 授权码与两家 LLM API Key，须加入 `.gitignore` 且勿提交；`1.sql` 预置管理员哈希未经明文验证（重置密码统一 `666666` 见 `AdminUserService`）
- 系统日志与 API 日志分离：`system_log` 记录服务端异常/越权告警，`api_log` 记录每次 AI 调用成败

## 9. Java 后端（spring-backend，2026-09-13 起为唯一后端）

Spring Boot 后端 `hello/spring-backend/`（**唯一后端**；原 Node/Express 版 `hello/backend/` 已于 2026-09-13 退役删除，其数据目录已改名为 `hello/data/`，现仅保留 `.env`、`uploads/`、`storage/` 共享运行时数据，请勿删除），与前端**共用同一 MySQL 库（`X`，11 张表，schema 不变）**。详见 `spring-backend/README.md`。

### 9.1 逻辑架构

```mermaid
flowchart TD
    FE["Vue3 前端 SPA（API_BASE_URL 可切换）"] -->|"HTTP/JSON + Bearer JWT :3000"| CTRL["Controller 13 个（路径与 Node 一致）"]
    CTRL --> SEC["Spring Security（JWT 过滤器，角色查库）"]
    CTRL --> RES["统一响应 Result 与全局异常"]
    CTRL --> SVC["Service（事务）"]
    SVC --> MAP["MyBatis-Plus Mapper"]
    MAP --> DB["MySQL 库 X（11 张表，schema 不变）"]
    SVC --> FS["uploads 与 storage 文件"]
    SVC --> LLM["DeepSeek / Qwen3.5（RestClient）"]
    SVC --> TEX["XeLaTeX（ProcessBuilder，30s）"]
    SVC --> MAIL["SMTP 邮件（JavaMailSender）"]
```

### 9.2 与 Node 版的关系

- **路径与入参完全不变**；响应体统一为 `{success, data, message}`（原 `{code,message,data}`、`{success,code,message,data}` 已收敛）。
- 技术映射：multer→`MultipartFile`、xlsx→POI、nodemailer→JavaMailSender、`child_process.exec(xelatex)`→`ProcessBuilder`、openai SDK/axios→`RestClient`、bcryptjs→`BCryptPasswordEncoder`、jsonwebtoken→jjwt。
- 存储路径沿用共享数据目录 `data/uploads`、`data/storage/history`、`data/storage/generated_charts`（`RELATIVE_BASE=..`），可复用历史文件与已生成 PDF（DB 中 `generation_path` 前缀已同步改为 `data\`）。
- 鉴权：无状态 JWT，角色从数据库读取后装配（延续「不信任 JWT 中角色声明」策略）。

### 9.3 端点与迁移顺序

13 个前缀一一对应：`/api/auth`、`/api/verification`、`/api/datasets`、`/api/chat`、`/api/compile`、`/api/history`、`/api/conversations`、`/api/feedback`、`/api/notice`、`/api/admin/{users,notices,log,static}`。迁移顺序：基础设施/鉴权 → auth/verification → datasets → chat → compile → history → conversations/feedback/notice → admin → 前端契约适配。

## 10. 参考文档

| 文档 | 说明 |
|---|---|
| `hello/README.md` | 项目文档 v2.0（模块/API/部署细节，差异见 §7） |
| `hello/spring-backend/README.md` | Java 后端（Spring Boot 3.2 + MyBatis-Plus 3.5）模块文档：构建/配置/映射/响应契约/验收 |
| `hello/plan.md` | 优化功能清单（部分契约未落地，见 §7#3-5） |
| `hello/log.md` | 开发日志 |
| `hello/1.sql`、`hello/migrations/`（含自 Node 迁移脚本归档的 `create_conversations_tables.sql`） | 数据库 schema 与迁移 |

---
**文档版本**：1.2（架构视图）
**基准日期**：2026-09-13
**基准**：`hello/` 下源码实证（行号引用如上）
