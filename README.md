<!-- 本地使用说明：后端 node app.js（backend 目录）或 npm run dev(nodemon)；前端 npm run serve（hello 根目录）；数据库 mysql -u root -p 登录输入密码 000; use X; source 1.sql; 并执行下方迁移（见 §4 快速开始）。 -->
<!-- AI 接口配置在 backend/.env（复制 backend/.env.example 填写，DEEPSEEK_* 与 NSCC_* 分别对应 DeepSeek 与 Qwen3.5）。 -->
<!-- 管理员账号预置：admin123 / 666666（见 1.sql 与 §8 版本说明）。 -->
# PGFPlotsGenerator 智能图表生成系统

> 通过自然语言（可附带 Excel/CSV 数据集）生成 PGFPlots/TikZ 图表代码，再用 XeLaTeX 编译为 PDF 在线预览与下载的全栈应用。前端 Vue 3 + 后端 Node.js/Express + MySQL。

- **普通用户**：对话式生成图表 → 编译 PDF → 历史回溯 / 数据集管理 / 多轮会话 / 反馈与通知
- **管理员**：用户管理、系统通知发布、反馈处理、系统日志与 API 统计
- **核心链路**：`自然语言(±数据集) → AI 生成 LaTeX → XeLaTeX 编译 → PDF 预览/下载`

> **版本**：本文档 v3.1，实证基准 2026-09-07，结论以 `hello/` 下源码为准。

## 1. 文档导航

> v3.0 起 README 收敛为「总入口」，细节不再重复维护，按需进入对应专项文档：

| 你想了解 | 文档 |
|---|---|
| 系统架构图、核心模块与数据流、数据库 ER | `docs/architecture.md` |
| 生产部署全流程、`.env.example`、备份/日志/排障 | `docs/deployment.md` |
| 本地开发、编码规范、二次开发/新增接口 | `docs/development.md` |
| **完整 API 接口契约（OpenAPI 3.0）** | `docs/openapi.yaml` |
| 迭代与部署流水记录 | `docs/log.md` |
| 功能优化规划 | `plan.md` |
| AI 编码代理协作规则 | `.github/copilot-instructions.md` |

## 2. 技术栈

| 层 | 选型（已核实） | 依据 |
|---|---|---|
| 前端 | Vue `^3.2.13` + Vue Router 4 + **Vuex 4**（非 Pinia）；Element Plus `^2.11.9` + `@element-plus/icons-vue`（无 Font Awesome）；ECharts `^6.0.0`（管理员监控）；axios（组件直调，**无 src/api 封装层**）；mitt 事件总线 | `package.json` |
| 构建 | **Vue CLI 5**（`vue-cli-service serve/build`，**非 Vite**） | `package.json`、`vue.config.js` |
| 后端 | Node.js + **Express `^4.18.2`**（backend/package.json，非 Express 5）；mysql2/promise 连接池；bcryptjs/jsonwebtoken | `backend/package.json` |
| AI 调用 | OpenAI SDK `^6.42.0` 调 **Qwen3.5**（湖大超算 NSCC MaaS）；axios 调 **DeepSeek**（`deepseek-v4-flash`） | `chat.js:352,398` |
| 编译 | `child_process.exec` 调 **XeLaTeX**（中文字体 SimSun/Times New Roman） | `compile.js:258-289` |
| 邮件 | nodemailer（QQ SMTP 发送验证码） | `services/verificationService.js` |
| 上传/解析 | multer（≤100MB）+ xlsx | `datasets.js`、`chat.js` |
| 存储 | MySQL（库名 `X`，**11 张表**）+ 文件系统（`uploads/`、`backend/storage/`） | `1.sql` + `migrations/` |

> 注意：`cors`、`multer` 被运行时 require 但**未声明在 `backend/package.json`**（依赖 node_modules），部署时务必确认已安装。

## 3. 目录结构

```
hello/
├── README.md                # 本文档（总入口）
├── 1.sql                    # 建库 X + 8 张基础表 + 预置管理员
├── migrations/              # SQL 迁移：notice 定向字段、notice_read 已读表
├── docs/                    # 专项文档：architecture / deployment / development / openapi.yaml / log
├── plan.md                  # 功能优化规划
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
└── backend/                 # 后端 Express
    ├── app.js               # 入口：中间件 + 13 个路由前缀（/api/admin/* 统一挂 JWT + 管理员角色鉴权）
    ├── db.js                # mysql2/promise 连接池（读 .env 的 DB_*，缺省回退 localhost/root/000/X，导出 promisePool）
    ├── .env.example / .env  # 环境变量模板 / 实际配置（勿提交 .env）
    ├── middleware/auth.js   # JWT 校验 authenticateToken
    ├── services/            # 业务服务（verificationService.js 邮件验证码）
    ├── utils/systemLog.js   # writeSystemLog → system_log（副作用，不抛异常）
    ├── migrations/          # 001_conversations.js（conversations / conversation_messages 两表）
    ├── routes/              # 13 个路由文件（见 §6）
    ├── uploads/             # 数据集原文件（multer）
    └── storage/             # history/{uid}/{id}.json、generated_charts/user{uid}/hist{id}.pdf
```

## 4. 快速开始（本地开发）

### 4.1 前置依赖

- Node.js ≥ 16、MySQL（本地密码默认 `000`，见 `backend/db.js`）、XeLaTeX（TeX Live，需含中文字体）

### 4.2 初始化数据库（库名 X，8 + 2 + 1 = 11 张表）

以下命令默认在**项目根（README 所在目录，即 hello/）**执行：

```bash
mysql -u root -p000          # 密码 000 与 db.js 一致
mysql> source 1.sql;                                      # 建库建表 + 预置管理员（注意会 DROP 重建）
mysql> source migrations/add_notice_feedback_columns.sql; # notice 表追加定向/反馈字段
mysql> source migrations/create_notice_read_table.sql;    # notice_read 每用户已读表
mysql> exit;

cd backend
node migrations/001_conversations.js                      # conversations / conversation_messages（须在 backend/ 目录执行）
cd ..
```

### 4.3 配置并启动后端（终端 A，在项目根）

```bash
cd backend
copy .env.example .env        # Windows；Linux/macOS 用 cp。至少填写 JWT_SECRET
# 若复制的是旧模板：确认 DB_PASSWORD/DB_NAME 不是 your-* 占位符，否则会报 Access denied；本地默认库为 localhost/root/000/X（见 backend/db.js 回退值）
npm install
npm run dev                   # nodemon 开发模式（或 npm run start / node app.js），默认 http://localhost:3000
```

### 4.4 启动前端（终端 B，在项目根）

```bash
npm install
npm run serve   # Vue CLI 开发服务器，默认 http://localhost:8080
```

### 4.5 验证

- 打开 `http://localhost:8080` 完成注册/登录
- 管理员：`http://localhost:8080` 登录页切「管理员登录」，账号 `admin123` / `666666`
- 在图表生成页输入示例如「绘制近五年出生人口条形图」→「生成 PDF」验证 XeLaTeX 链路

> 生产部署（Linux/宝塔/pm2/nginx/TeX 安装坑）见 `docs/deployment.md`。

## 5. 核心功能与模块速览

### 5.1 用户能力

| 能力域 | 后端入口 | 前端页面 | 说明 |
|---|---|---|---|
| 认证 | `routes/auth.js` + `middleware/auth.js` | Login/Register/AdminLogin、ChangeInformation | 注册（邮箱验证码）/登录/改密码用户名邮箱；JWT 用户 24h、管理员 7d |
| AI 生成 | `routes/chat.js` | ChartGenerator | 调 Qwen3.5 / DeepSeek 生成 PGFPlots 代码并落库；空回复返回 502 明确错误 |
| 编译 | `routes/compile.js` | ChartGenerator | 读库内代码 → XeLaTeX 编译 → 更新 PDF 路径 |
| 历史 | `routes/history.js` | MyHistory | 列表/详情/删除（事务级联 api_log）/统计/CSV 导出 |
| 数据集 | `routes/datasets.js` | DataUpload、ChartGenerator | multer 上传 ≤100MB/列表/改名/删除/下载 |
| 多轮会话 | `routes/conversations.js` | ChartGenerator | 会话 CRUD + 消息读取；chat 生成时联动落 `conversation_messages` |
| 反馈 | `routes/feedback.js` | MyFeedback | 提交/查看反馈；管理员回复后自动生成定向通知 |
| 通知 | `routes/notice.js` | MyNotice、导航栏 | 系统广播+反馈回复统一列表，已读走 `notice_read`，导航栏未读红点 |
| 验证码 | `routes/verification.js` + `services/verificationService.js` | Register/ChangeInformation | 6 位验证码，10 分钟有效、一次性 |

### 5.2 管理员能力（`/admin/*` 页面，`src/views/` + `src/Admin.vue`）

| 能力域 | 后端入口 | 前端页面 | 说明 |
|---|---|---|---|
| 概览 | `routes/AdminStatic.js` | Admin.vue | 全库计数（用户/文件/生成/反馈） |
| 用户管理 | `routes/AdminUser.js` | AdminUser | 列表/重置密码为 666666/删除（级联）/统计 |
| 通知管理 | `routes/AdminNotice.js` | AdminNotice | 系统通知 CRUD（广播 target_user_id=NULL）+ 已读人数 |
| 系统监控 | `routes/AdminLog.js` | AdminLog | API 统计/系统日志/健康概览/表状态 |
| 反馈处理 | `routes/feedback.js`（checkAdmin 校验） | AdminFeedback | 列表/详情/回复/删除，回复联动写通知 |

> 模块/数据流/ER 详图见 `docs/architecture.md`；完整接口定义见 `docs/openapi.yaml`。

## 6. 配置要点

| 配置 | 位置 | 事实说明 |
|---|---|---|
| 环境变量 | `backend/.env.example` → 复制为 `backend/.env` | `JWT_SECRET`、SMTP_*（QQ 授权码）、`DEEPSEEK_API_KEY/URL`、`NSCC_API_KEY/URL`；各键用途见模板注释 |
| 后端地址 | `src/config.js` 的 `API_BASE_URL` | 默认 `http://localhost:3000`；前端所有页面统一从这里取值 |
| 数据库连接 | `backend/db.js` | 读取 `.env` 的 `DB_HOST/DB_USER/DB_PASSWORD/DB_NAME`，未设置时回退本地默认值 `localhost/root/000/X` |

## 7. 安全与错误处理要点

- JWT 鉴权：`middleware/auth.js` 验签并查库注入 `req.user`（不含 role）；数据隔离按 `user_id = ?`
- 密码：bcrypt 哈希；规则「仅字母数字、6–16 位」（`auth.js` `validatePassword`）
- SQL：全部参数化查询
- AI 空回复兜底：Qwen/DeepSeek 返回空 content 时记录原始响应、写系统日志并返回 `502`（`chat.js:372-384 / 421-429`）
- 编译失败：返回 stderr、临时目录 `safeCleanup` 必清理
- ✅ 已加固（2026-09-07，详见 `docs/log.md`）：`/api/admin/*` 四个模块统一挂 `authenticateToken + requireAdmin`；PDF 移除 `/storage` 无鉴权静态托管，改 `GET /api/compile/:id/pdf` 归属校验流式返回；LaTeX 编译前危险序列校验；`db.js` 凭据改读 `.env`；JWT 去除兜底密钥（缺失启动即退出）；密码下限 6 位
- ⚠️ 运维注意：`.env` 含 SMTP/LLM 密钥须保密勿提交；`1.sql` 预置管理员哈希未经明文验证（重置密码统一 `666666` 见 `AdminUser.js:96`）

## 8. 版本说明

| 版本 | 日期 | 说明 |
|---|---|---|
| v3.1 | 2026-09-07 | **全量 P0 安全加固 + 配置修复**：`/api/admin/*` 统一挂 `authenticateToken + requireAdmin`；PDF 移除无鉴权静态托管改归属校验流式返回（前端 Blob 预览）；LaTeX 编译前危险序列校验；`db.js` 凭据改读 `.env`（缺省回退本地默认）；JWT 去除兜底密钥（缺失启动即退出）；密码 6-16 位；修复 `.env` 旧占位 `DB_*` 导致的本地连库失败（详见 `docs/log.md` 2026-09-07） |
| v3.0 | 2026-09-05 | **精简重构为总入口**；修正与代码不一致处（如后端路由前缀实为 **13 个**，非 v2.0 所述 14 个）；详细 API/部署/架构内容迁至 docs/ 专项文档，避免多份重复维护 |
| v2.0 | 2026-07-16 | 旧版主文档（API 明细等已迁移，其「与旧文档差异」并入 `docs/architecture.md` §7） |

**预置管理员账号**：`admin123` / `666666`（`1.sql:14-20`）。

---
**文档版本**：3.1
**最后更新**：2026-09-07
**基准**：`hello/` 下实际源码与 SQL/迁移文件（行号引用同 `docs/architecture.md`）
