<!-- 本地使用说明：启动后端 node app.js（backend 目录）；前端 npm run serve；数据库 mysql -u root -p登录输入密码000; use X; source 1.sql; 并按"部署与配置"执行迁移。 -->
<!-- 修改 AI 接口在 backend/.env（DEEPSEEK_* 与 NSCC_* 分别对应 DeepSeek 与 Qwen 模型）。 -->
<!-- 管理员账号已预置：admin123 / 666666（见 1.sql）。 -->
# PGFPlotsGenerator 智能图表生成系统 — 项目文档

> **文档说明**：本文档基于 `2026-07-16` 的代码实证编写，所有结论均以项目实际文件为绝对基准（行号引用见正文）。文末「与旧文档主要差异」逐条列出对旧 README 的更正，确保文档与代码真实状态一致。
>
> **范围**：普通用户（自然语言 → AI 生成 PGFPlots/TikZ 代码 → XeLaTeX 编译 PDF/PNG → 查看/下载/历史）与管理员（用户、反馈、通知、系统监控）两类角色。

## 1. 项目概述

PGFPlotsGenerator 是一个「智能图表生成系统」：用户用自然语言描述需求（可附带 Excel/CSV/文本数据集），后端调用大语言模型生成 LaTeX/PGFPlots 代码，再通过 XeLaTeX 编译为 PDF 供在线预览与下载；生成过程持久化到 MySQL，支持历史回溯、多轮对话保存与反馈通知。

- **前端**：Vue 3 + Element Plus，Vue Router 4 路由，Vuex 4 状态管理，ECharts 用于管理员监控页。
- **后端**：Node.js + Express 4，`mysql2/promise` 连接池，XeLaTeX 编译，OpenAI SDK 调 Qwen（湖大超算 MaaS）、axios 调 DeepSeek。
- **存储**：MySQL（库名 `X`）+ 本地文件系统（`storage/`、`uploads/`）。

## 2. 技术栈（已核实）

### 前端（`hello/package.json`）
- **Vue** `^3.2.13`、`vue-router` `^4.6.3`、`vuex` `^4.0.2`（状态管理，**非 Pinia**）
- **Element Plus** `^2.11.9` + `@element-plus/icons-vue` `^2.3.2`（图标，**无 Font Awesome**）
- **ECharts** `^6.0.0`（管理员监控图表）
- **axios** `^1.13.2`（组件内直接调用）
- **mitt** `^3.0.1`（事件总线）
- 构建：**Vue CLI 5**（`@vue/cli-service ~5.0.0`，脚本 `npm run serve`，**非 Vite**）
- 开发依赖：`playwright`、`eslint` 等

### 后端（`backend/package.json`，包名 `my-google-style-app`）
- **express** `^4.18.2`（**非 Express 5**）
- **mysql2** `^3.15.3`（连接池 `promisePool`）、`mysql` `^2.18.1`
- **openai** `^6.42.0`（调 Qwen）、**axios** `^1.13.2`（调 DeepSeek）
- **xlsx** `^0.18.5`（数据集解析）、**bcryptjs** `^3.0.3`/`bcrypt`、`jsonwebtoken` `^9.0.2`
- **nodemailer** `^7.0.11`（邮箱验证码）、**dotenv** `^16.3.1`、`express-validator` `^7.3.1`、`uuid`
- 注意：`app.js` 用到 `cors`、`datasets.js` 用到 `multer`，但二者未声明在 `backend/package.json` 的依赖中（实际由已安装的 `node_modules` 提供）。**部署时务必确认 `cors` 与 `multer` 已安装**，否则后端无法启动/上传失败（依赖声明待补全）。

## 3. 目录结构（真实）

```
hello/
├── 1.sql                      # 基础建表（users/data_file/generation_history/api_log/feedback/system_log/email_verification_codes/notice）
├── migrations/
│   ├── add_notice_feedback_columns.sql   # 为 notice 追加定向/反馈字段（方案 B）
│   └── create_notice_read_table.sql     # 创建 notice_read（已读记录表）
├── backend/
│   ├── app.js               # 入口：中间件 + 14 个路由前缀挂载 + 静态目录（app.js:2-41）
│   ├── db.js                # 连接池（硬编码 localhost/root/000/X，未读 .env 的 DB_*）
│   ├── .env                # 端口/JWT/邮件/AI 密钥（明文，生产应改用密钥管理）
│   ├── middleware/auth.js  # JWT 校验中间件 authenticateToken
│   ├── services/verificationService.js  # 验证码生成/发信/存储/校验（落 email_verification_codes）
│   ├── utils/systemLog.js  # writeSystemLog(status,message) → system_log（失败只 console，不抛出）
│   ├── migrations/001_conversations.js   # 创建 conversations / conversation_messages
│   └── routes/
│       ├── auth.js  chat.js  compile.js  datasets.js  history.js
│       ├── feedback.js  notice.js  verification.js
│       ├── AdminNotice.js  AdminUser.js  AdminLog.js  AdminStatic.js
│       └── conversations.js
└── src/                    # 前端（Vue CLI 工程，无 src/api 统一封装层）
    ├── main.js  config.js  App.vue  router/index.js  store/index.js
    ├── components/   (TheAuth/LoginForm/RegisterForm/AdminLogin/CommonNavbar/CommonSidebar/AdminNavbar/AdminSidebar + ui/ 通用组件)
    └── views/        (Admin/AdminFeedback/AdminLog/AdminNotice/AdminUser/ChartGenerator/ChangeInformation/DataUpload/MyFeedback/MyHistory/MyNotice)
```

## 4. 核心功能模块说明

| 模块 | 入口文件 | 职责 | 关键函数/表 |
|---|---|---|---|
| 认证 | `routes/auth.js` | 用户注册/登录、管理员登录、改密码/用户名/邮箱、token 校验；密码 `bcrypt` 哈希，统一 `validatePassword`（仅字母数字、1-8 位） | `users` |
| AI 生成 | `routes/chat.js` | 接收自然语言+数据集，构建系统提示词，按 `model` 调 DeepSeek/Qwen，提取图表代码并落库 | `buildMessagesWithDataset`、`extractChartCode`、`saveGenerationHistory`、`generation_history`/`api_log` |
| 编译 | `routes/compile.js` | 读取历史代码 → 预处理 → 包中文文档 → XeLaTeX 编译 → 存 PDF | `preprocessLatexCode`、`createChineseLatexDocument`、`compileLatexWithXeLaTeX`；`storage/generated_charts/user{id}/hist{id}.pdf` |
| 历史 | `routes/history.js` | 历史列表/详情/删除（事务级联删 api_log）/统计/CSV 导出 | `generation_history`/`api_log` |
| 数据集 | `routes/datasets.js` | 上传（multer，≤100MB）/列表/改名/删除/下载 | `data_file`；`readFileContent` 在此文件内定义（chat.js 同源复用） |
| 对话持久化 | `routes/conversations.js` | 多轮会话列表/新建/改名/删除/取消息；chat.js 生成时若有 `conversation_id` 写 `conversation_messages` | `conversations`/`conversation_messages` |
| 反馈 | `routes/feedback.js` | 用户提交反馈、查看自己的反馈、管理员列表/详情/回复/删除；回复时联动写通知 | `feedback`/`notice` |
| 通知 | `routes/notice.js` | 用户通知列表/单条已读/全部已读/未读计数（系统通知 + 反馈回复统一走 `notice`+`notice_read`） | `notice`/`notice_read` |
| 邮件验证码 | `routes/verification.js` + `services/verificationService.js` | 发送/校验注册验证码（落 `email_verification_codes`，10 分钟过期，一次性） | `email_verification_codes` |
| 管理员后台 | `AdminUser/AdminNotice/AdminLog/AdminStatic.js` | 用户管理（重置密码为 666666、级联删除）、通知管理、系统日志/API 统计/健康概览、汇总统计 | 多表 |

> 说明：`notice` 表经迁移新增 `target_user_id / feedback_id / feedback_time / feedback_type / reply` 五列（见 `migrations/add_notice_feedback_columns.sql`）；反馈被回复后自动写入一条定向通知（`feedback.js:327-353`），用户在「我的通知」中可见。

## 5. API 接口定义

所有接口以 `/api` 为前缀挂载（`app.js:27-39`），共 **14 个路由模块**。除标注外，业务接口均需 `Authorization: Bearer <token>` 请求头（经 `authenticateToken`）。

### 5.1 /api/auth（`auth.js`）
| 方法 | 路径 | 请求体 | 说明 | 涉及表 |
|---|---|---|---|---|
| POST | `/admin/login` | `{adminAccount, password}` | 管理员登录（role=admin），返回 token | `users` |
| POST | `/register` | `{username, email, password, verificationCode}` | 注册（验证码校验 + 唯一性），返回 token | `users`/`email_verification_codes` |
| POST | `/login` | `{email, password}` | 用户登录，返回 token | `users` |
| POST | `/change-password` * | `{currentPassword, newPassword}` | 改密码（校验旧密码+格式） | `users` |
| POST | `/change-username` * | `{newUsername}` | 改用户名（≤10，字母数字下划线中文） | `users` |
| POST | `/change-email` * | `{newEmail, verificationCode}` | 改邮箱（验证码校验） | `users` |
| GET | `/validate` * | — | 校验 token，返回当前用户 | `users` |

### 5.2 /api/chat（`chat.js`）
| 方法 | 路径 | 请求体 | 说明 | 涉及表 |
|---|---|---|---|---|
| POST | `/` * | `{message, data_ids?, chart_code?, model?, conversation_id?, selected_files?}` | 生成图表代码；`model='qwen'` 走 OpenAI SDK 调 Qwen3.5，否则 axios 调 DeepSeek（`deepseek-v4-flash`）；空回复返回 502 明确错误；返回 `{reply, chart_code, history_id, usage}` | `generation_history`/`api_log`/`data_file`（+`conversation_messages` 若传 `conversation_id`） |

> **不存在** `/api/chat/fix`（旧文档误列）。修复编译错误的能力未经后端实现，目前由前端/用户手动改码重编译。

### 5.3 /api/compile（`compile.js`）
| 方法 | 路径 | 说明 | 涉及表 |
|---|---|---|---|
| GET | `/:history_id/pdf-url` * | 返回已生成 PDF 的访问 URL | `generation_history` |
| POST | `/:history_id` * | 读取该历史的 `generation_code` → 编译 → 移动 PDF 到 `storage/generated_charts/user{id}/hist{id}.pdf`，更新 `generation_path` | `generation_history` |

> **重要**：`POST /:history_id` **只读取库内 `generation_code`**，不接受请求体 `code` 字段覆盖（旧文档「支持 body.code 覆盖」不实）。要改码重编译，需先更新库内代码再调此接口，或另写前端覆盖逻辑。

### 5.4 /api/datasets（`datasets.js`）
| 方法 | 路径 | 请求体/参数 | 说明 |
|---|---|---|---|
| GET | `/` * | `?keyword=` | 我的数据集列表（分页/搜索，含 `size` 格式化） |
| POST | `/` *（multer `single('file')`） | `{name, description}` | 上传（≤100MB），落 `data_file` + `uploads/` |
| POST | `/:id/update` * | `{name?, description?}` | 改名/描述 |
| DELETE | `/:id` * | — | 删除（级联删文件） |
| GET | `/download/:id` * | — | 下载原文件 |

> **不存在** `/api/datasets/:id/preview`（旧文档误列）。文件内容预览当前由前端读取，未走后端独立接口。

### 5.5 /api/history（`history.js`，均 *）
- `GET /` `?page&limit&search&start_date&end_date`：历史列表
- `GET /:id`：详情
- `DELETE /:id`：删除（事务：先删 `api_log` 再删 `generation_history`）
- `GET /stats/summary`：本周/总数统计
- `GET /export/csv`：导出 CSV

### 5.6 /api/feedback（`feedback.js`）
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/` * | 用户 | 提交反馈：`{type, content}`，`type∈{suggestion,ui,bug,other}`，`content≤100` |
| GET | `/user/my-feedbacks` * | 用户 | 我的反馈（含状态 待回复/已回复） |
| GET | `/` * | 管理员 | 全部反馈（筛选 type/日期） |
| GET | `/:id` * | 管理员 | 详情 |
| PUT | `/:id/reply` * | 管理员 | 回复（`{answer≤500}`），**同时写一条定向通知到 `notice`** |
| DELETE | `/:id` * | 管理员 | 删除 |

### 5.7 /api/verification（`verification.js`，**无需 token**）
- `POST /send-register-code` `{email}`：发送注册验证码
- `POST /verify-register-code` `{email, code}`：校验（一次性）

### 5.8 /api/notice（`notice.js`，均 *）
- `GET /`：通知列表（系统+反馈回复）+ `unreadCount`
- `POST /read/:id`：标记单条已读（写 `notice_read`）
- `POST /read-all`：全部已读
- `GET /unread-count`：未读计数（供导航栏红点）

### 5.9 管理员接口（均无 JWT 中间件，见第 10 节安全说明）
| 模块 | 路径前缀 | 主要接口 |
|---|---|---|
| `AdminUser.js` | `/api/admin/users` | `GET /`（列表）、`PATCH /:id/reset-password`（重置为 666666）、`DELETE /:id`（级联删关联数据）、`GET /statistics/overview` |
| `AdminNotice.js` | `/api/admin/notices` | `GET /`（列表+统计）、`GET /:id`、`POST /`（`admin_id` 硬编码 1）、`PUT /:id`、`DELETE /:id`、`DELETE /`（批量）、`GET /statistics/overview` |
| `AdminLog.js` | `/api/admin/log` | `GET /api-stats`、`GET /system-logs`、`GET /health-overview`、`POST /add-test-log`、`GET /tables-status` |
| `AdminStatic.js` | `/api/admin/static` | `GET /`（users/files/generations/feedback 计数） |

### 5.10 /api/conversations（`conversations.js`，均 *）
- `GET /`：对话列表（含最后消息预览、消息数）
- `POST /` `{title?}`：新建
- `PUT /:id` `{title}`：重命名
- `DELETE /:id`：删除（级联删 `conversation_messages`）
- `GET /:id/messages`：取某对话全部消息

## 6. 数据流走向

### 6.1 生成 → 编译主链路
```
用户(ChartGenerator) ──POST /api/chat──▶ chat.js
   ├─ buildMessagesWithDataset(): 查 data_file + readFileContent() 拼数据集进系统提示词
   ├─ 调模型: model='qwen'? OpenAI→Qwen3.5 : axios→DeepSeek(deepseek-v4-flash)
   ├─ extractChartCode(aiReply) 抽取 \begin{tikzpicture}…\end{tikzpicture}
   ├─ saveGenerationHistory(): 写 generation_history + storage/history/{userId}/{id}.json + api_log(success/failed)
   └─ 若传 conversation_id: persistConversation() 写 conversation_messages
        ▼ 返回 history_id
用户 ──POST /api/compile/:history_id──▶ compile.js
   ├─ 查询 generation_code（只读库内，不接受 body.code）
   ├─ preprocessLatexCode(): 抽取 \begin/\end{document} 之间内容
   ├─ createChineseLatexDocument(): 包 standalone + xeCJK(SimSun) 中文字体 + 注入 pgfplots/amsmath 等宏包
   ├─ compileLatexWithXeLaTeX(): xelatex -interaction=nonstopmode（超时 30s）
   ├─ 成功: 移动 PDF → storage/generated_charts/user{userId}/hist{historyId}.pdf，更新 generation_path
   └─ 返回 pdf_url = /storage/generated_charts/user{id}/hist{id}.pdf
```
PDF 通过 `app.use('/storage', express.static(...))`（`app.js:41`）静态托管，前端直接 `<iframe>/<img>` 预览。

### 6.2 鉴权流
`登录/admin-login` → `jwt.sign({userId,email,role})`（密钥 `JWT_SECRET`，用户 24h / 管理员 7d）→ 前端存 `localStorage/sessionStorage` 的 `token` → 请求头 `Authorization: Bearer <token>` → `authenticateToken` 验签并查 `users` 注入 `req.user`（`middleware/auth.js:7-38`）。

### 6.3 对话持久化
`chat.js` 生成成功后，若前端传 `conversation_id`，将用户消息与助手回复（含 `chart_code`/`history_id`）写入 `conversation_messages`；`conversations` 表首条消息自动取用户输入前 20 字为标题。

### 6.4 反馈 → 通知联动
管理员 `PUT /api/feedback/:id/reply` → 更新 `feedback.answer` → 插入一条 `notice`（`target_user_id`=反馈者，`feedback_id` 关联，类型映射 suggestion/ui/bug/other）→ 用户 `GET /api/notice` 看到 `type='feedback'` 的通知，可 `POST /read/:id` 标记已读（写 `notice_read`）。

### 6.5 存储路径约定
- `uploads/`：数据集原始文件（multer）
- `storage/history/{userId}/{historyId}.json`：完整对话/生成记录
- `storage/generated_charts/user{userId}/hist{historyId}.pdf`：编译产物
- `/storage`：静态根（`app.js:41`）

## 7. 依赖关系

```
前端 (Vue CLI 工程)
   │  HTTP/JSON，Bearer JWT
   ▼
后端 Express (app.js 挂载 14 路由前缀)
   ├─▶ MySQL (mysql2/promise 连接池, db.js)
   ├─▶ XeLaTeX（child_process.exec，编译 LaTeX）
   ├─▶ LLM：Qwen3.5(OpenAI SDK→NSCC) / DeepSeek(axios→DeepSeek API)
   ├─▶ SMTP（nodemailer，发验证码）
   └─▶ 文件系统（uploads/、storage/）
```
- **前端 → 后端**：组件内直接用 `axios`/`fetch` 请求（**无 `src/api` 统一封装层**），基址来自 `src/config.js` 的 `API_BASE_URL`（默认 `http://localhost:3000`）。
- **后端 → 前端**：JWT 鉴权 + `success/data` 或 `code/message/data` 混合响应结构（两类风格并存，前端分别处理）。
- **第三方库映射**：状态管理 Vuex；UI Element Plus + icons；图表 ECharts；AI openai/axios；DB mysql2；邮件 nodemailer；文件 xlsx/multer；密码 bcryptjs；令牌 jsonwebtoken。

## 8. 数据库设计（实际 schema）

库名 `X`。基础表在 `1.sql`，补充表/列在 `migrations/`。

| 表 | 关键列 | 来源 |
|---|---|---|
| `users` | user_id, username(≤10), email(≤20), password(bcrypt), role(enum user/admin), register_time | `1.sql:5` |
| `data_file` | data_id, user_id, data_name, data_size, description, load_time, update_time, file_name, file_path, mimetype | `1.sql:22` |
| `generation_history` | history_id, user_id, data_id, generation_description, generation_code(text), generation_path, generation_time | `1.sql:36` |
| `api_log` | call_id, user_id, history_id, call_status(enum success/failed), call_time, call_error | `1.sql:48` |
| `feedback` | feedback_id, user_id, type(enum suggestion/ui/bug/other), content, feedback_time, answer, answer_time | `1.sql:59` |
| `system_log` | sys_id, system_status(enum normal/warning/error), log_time, error | `1.sql:70` |
| `email_verification_codes` | id, email, code(6), created_at, expires_at | `1.sql:77` |
| `notice` | notice_id, title, content, admin_id, **target_user_id, feedback_id, feedback_time, feedback_type, reply**（后 5 列由迁移追加）, created_time | `1.sql:87` + `add_notice_feedback_columns.sql` |
| `notice_read` | id, user_id, notice_id, read_time（唯一索引 user+notice） | `create_notice_read_table.sql` |
| `conversations` | conversation_id, user_id, title(default '新对话'), created_at, updated_at | `backend/migrations/001_conversations.js:8` |
| `conversation_messages` | message_id, conversation_id, user_id, role, content(text), chart_code, history_id, selected_files, created_at | `backend/migrations/001_conversations.js:19` |

**部署执行顺序（建表/迁移）**：
```bash
mysql -u root -p000 X < 1.sql
mysql -u root -p000 X < migrations/add_notice_feedback_columns.sql
mysql -u root -p000 X < migrations/create_notice_read_table.sql
node backend/migrations/001_conversations.js   # 创建 conversations/conversation_messages
```
> 注意：`conversations` 与 `conversation_messages` 由 JS 迁移脚本创建（非 SQL 文件），**未执行则对话持久化接口会报错**。

## 9. 部署与配置

### 启动
```bash
# 后端（backend 目录）
cd backend && npm install && node app.js          # 监听 PORT（默认 3000，0.0.0.0）
# 前端（hello 根目录）
npm install && npm run serve                    # Vue CLI 开发服务器
```
### 配置
- **数据库连接**：`backend/db.js` **硬编码** `host=localhost, user=root, password=000, database=X`，且不读取 `.env` 的 `DB_*`（`.env:8-12` 注释亦说明）。改库需直接改 `db.js`。
- **环境变量**（`backend/.env`，明文，生产应改用密钥管理）：
  - `PORT`、`JWT_SECRET`
  - 邮件：`SMTP_HOST/PORT/USER/PASS/FROM`（QQ 示例）
  - AI：`DEEPSEEK_API_KEY`/`DEEPSEEK_API_URL`、`NSCC_API_KEY`/`NSCC_API_URL`
- **TeX 环境**：需安装 TeX Live 并含 `xelatex` 与中文字体（代码默认 `SimSun` + `Times New Roman`，见 `compile.js:247-248`）。

### 管理员账号
预置于 `1.sql`：`admin123` / `666666`；管理员在前端「用户管理」重置任意用户密码时统一为 `666666`（`AdminUser.js:96`）。

## 10. 安全与错误处理

- **认证**：JWT（`jsonwebtoken`），`authenticateToken` 验签后查库注入 `req.user`；用户 token 24h、管理员 7d。
- **密码**：`bcryptjs` 哈希；`validatePassword` 强制「仅字母数字、1-8 位」（注册/改密共用）。
- **SQL 注入**：全部使用参数化查询（`?` 占位）。
- **数据隔离**：历史/数据集/对话/通知接口均带 `user_id = ?` 条件，越权访问返回 404。
- **空回复兜底**：AI 返回空 content 时 `chat.js` 记日志并返回 `502` 明确错误，绝不静默返回空代码。
- **编译错误处理**：XeLaTeX 超时 30s；失败返回 `stderr` 拼接信息；临时目录 `safeCleanup` 必清理。
- **系统日志**：`writeSystemLog(status, message)` 统一写 `system_log`（截断 500 字、不抛异常）；`api_log` 记录每次生成成功/失败。
- **⚠ 已知待加固**：`/api/admin/*`（AdminUser/AdminNotice/AdminLog/AdminStatic）**未挂载 JWT/管理员鉴权中间件**，仅依赖前端管理员页限制；`AdminNotice` 创建通知时 `admin_id` 硬编码为 `1`。生产环境应补齐服务端鉴权，避免越权访问。
- **密钥管理**：`.env` 含明文 SMTP 密码与 AI Key，应纳入密钥管理/.gitignore，勿提交生产凭据。

## 11. 与旧文档主要差异（更正清单）

| # | 旧 README 声称 | 代码真实状态 | 证据 |
|---|---|---|---|
| 1 | 「Vite」构建工具 | 实际 **Vue CLI 5**（`vue-cli-service serve`） | `hello/package.json:5-9` |
| 2 | 「Font Awesome 部分组件使用」 | **无 Font Awesome**；图标全用 `@element-plus/icons-vue` | `package.json` 依赖无 fa；`main.js:29-39` |
| 3 | 「Lodash 防抖」 | **无 Lodash**；防抖为手写 `debounce` | `src/main.js:4-14` |
| 4 | 「ResizeObserver Polyfill」依赖 | **无此包**；`main.js` 重写 `window.ResizeObserver` + 手写防抖解决 | `src/main.js:3-23` |
| 5 | `config.js` 硬编码 `1.92.74.67:8889` | 默认 **`http://localhost:3000`**（服务器 IP 仅注释） | `src/config.js:2-4` |
| 6 | `db.js` `localhost/mydb/mydb/X` | 实际 **`localhost/root/000/X`**，且未读 `.env` 的 `DB_*` | `backend/db.js:3-11` |
| 7 | Express 5 / 后端包名误导 | 后端实际 **express `^4.18.2`** | `backend/package.json:25` |
| 8 | 路由缺 `/verification`、`/conversations`、`/admin/static`、`/admin/log`，误列 `/api/user` | 真实 **14 个前缀**（见 §5）；无 `/api/user` | `backend/app.js:27-39` |
| 9 | 存在 `/api/chat/fix`、`/api/datasets/preview`、compile 接受 `body.code` | **三者均不存在**（规划中能力，非已实现） | `chat.js:319` 单路由；`compile.js:67` 仅读库内代码；`datasets.js` 无 preview |
| 10 | compile「自动检测和修复中文支持/字体替换/宏包注入」 | 实际为固定流程：`preprocessLatexCode` 抽取文档内容 + `createChineseLatexDocument` 包 standalone、注入 xeCJK(SimSun) 与 pgfplots 等宏包 | `compile.js:208-255` |
| 11 | `notice` 表仅基础字段 | 迁移新增 `target_user_id/feedback_id/feedback_time/feedback_type/reply`，反馈回复自动写通知 | `add_notice_feedback_columns.sql`、`feedback.js:327-353` |
| 12 | 仅列 `1.sql` 的表 | `notice_read`、`conversations`、`conversation_messages` 建在迁移文件，需额外执行 | `create_notice_read_table.sql`、`backend/migrations/001_conversations.js` |
| 13 | 管理员账号「已存在 admin123/666666」 | ✅ 正确（预置 `1.sql`；重置密码统一 666666） | `1.sql:14-20`、`AdminUser.js:96` |

---
**文档版本**：2.0
**最后更新**：2026-07-16
**基准**：以 `hello/` 下实际源码与 SQL/迁移文件为准
