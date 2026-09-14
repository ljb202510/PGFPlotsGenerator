<!-- 本地使用说明：后端 cd spring-backend && run.cmd（开发模式 mvn-run.cmd）；前端 npm run serve（hello 根目录）；数据库 mysql -u root -p 登录输入密码 000; use X; source 1.sql; 并执行下方迁移（见 §4 快速开始）。 -->
<!-- AI 接口配置在 data/.env（Java 后端启动时自动读取 ../data/.env：NSCC_* → Qwen3.5（主通道）、SILICONFLOW_* → GLM-4.9（降级链第二层）、DEEPSEEK_* → DeepSeek（当前 DEEPSEEK_ENABLED=false 停用，余额为 0））。 -->
<!-- 注意：原 Node/Express 后端已于 2026-09-13 退役删除（移至本机D盘/PG）；其数据目录 backend/ 已改名为 data/，现仅保留 .env、uploads/、storage/ 共享运行时数据（Java 后端仍在使用），请勿删除。 -->
<!-- 管理员账号预置：admin123 / 666666（见 1.sql 与 §8 版本说明）。admin@pgfplots.com user_id=1 -->
# PGFPlotsGenerator 智能图表生成系统

> 通过自然语言（可附带 Excel/CSV 数据集）生成 PGFPlots/TikZ 图表代码，再用 XeLaTeX 编译为 PDF 在线预览与下载的全栈应用。前端 Vue 3 + 后端 Spring Boot（`spring-backend/`）+ MySQL（原 Node/Express 后端已退役）。

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
| 后端 | **Spring Boot `3.2.12` + MyBatis-Plus `3.5.5`**（`spring-backend/`，Java 17）；HikariCP 连接池；Spring Security 无状态 JWT + BCrypt；响应统一 `{success,data,message}` | `spring-backend/README.md` |
| 后端（已退役） | 原 Node.js + Express 后端（13 路由）已于 2026-09-13 全量退役删除，Java 版接口路径与行为等价 | `docs/log.md` |
| AI 调用 | Spring 6 `RestClient` 调 **Qwen3.5**（湖大超算 NSCC MaaS）与 **DeepSeek**（`deepseek-v4-flash`） | `client/LlmClient.java` |
| 编译 | `ProcessBuilder` 调 **XeLaTeX**（30s 超时；中文字体 SimSun/Times New Roman） | `util/LatexCompiler.java` |
| 邮件 | spring-boot-starter-mail（JavaMailSender，QQ SMTP 发送验证码） | `service/VerificationService.java` |
| 上传/解析 | Spring `MultipartFile`（≤100MB）+ Apache POI 5.2.5（xlsx） | `controller/DatasetController.java`、`util/FileContentReader.java` |
| 存储 | MySQL（库名 `X`，**11 张表**）+ 文件系统（`data/uploads/`、`data/storage/`，Java 默认沿用） | `1.sql` + `migrations/` |

> 注意：`data/` 目录（由原 Node 后端目录 `backend/` 改名而来）现为**共享运行时数据目录**，仅保留 `.env`（Java 启动依赖）、`uploads/`、`storage/`，请勿删除该目录。

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
├── spring-backend/          # ★ 后端：Spring Boot + MyBatis-Plus（唯一后端，见 spring-backend/README.md）
├── data/                    # 共享运行时数据目录（原 backend/ 改名而来，请勿删除）
    ├── .env                 # 环境配置（Java 启动时自动读取；含 DB/SMTP/LLM 密钥，勿提交 .env）
    ├── uploads/             # 数据集原文件（Java 默认 UPLOADS_DIR 指向此处）
    └── storage/             # history/{uid}/{id}.json、generated_charts/user{uid}/hist{id}.pdf
```

## 4. 快速开始（本地开发）

### 4.1 前置依赖

- JDK 17+（构建/运行后端）、Node.js ≥ 16（仅前端构建）、MySQL（本地密码默认 `000`，读 `backend/.env` 的 DB_*）、XeLaTeX（TeX Live，需含中文字体）

### 4.2 初始化数据库（库名 X，8 + 2 + 1 = 11 张表）

以下命令默认在**项目根（README 所在目录，即 hello/）**执行：

```bash
mysql -u root -p000          # 密码 000 与 db.js 一致
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
# 配置自动读取 ../data/.env（DB/SMTP/LLM 密钥），无需复制模板；JWT_SECRET 缺失会启动失败（fail-fast）
run.cmd                       # 自动选 JDK 17+ 启动 jar（jar 不存在则先构建），默认 http://localhost:3000
# 开发模式可用 mvn-run.cmd（mvn spring-boot:run）；详见 spring-backend/README.md §4
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
| 认证 | `controller/AuthController` + `security/JwtAuthenticationFilter` | Login/Register/AdminLogin、ChangeInformation | 注册（邮箱验证码）/登录/改密码用户名邮箱；JWT 用户 24h、管理员 7d |
| AI 生成 | `controller/ChatController`（`service/ChatService`） | ChartGenerator | 调 Qwen3.5 / DeepSeek 生成 PGFPlots 代码并落库；空回复返回 502 明确错误 |
| 编译 | `controller/CompileController`（`service/CompileService`） | ChartGenerator | 读库内代码 → XeLaTeX 编译 → 更新 PDF 路径 |
| 历史 | `controller/HistoryController` | MyHistory | 列表/详情/删除（事务级联 api_log）/统计/CSV 导出 |
| 数据集 | `controller/DatasetController` | DataUpload、ChartGenerator | 上传 ≤100MB/列表/改名/删除/下载 |
| 多轮会话 | `controller/ConversationController` | ChartGenerator | 会话 CRUD + 消息读取；chat 生成时联动落 `conversation_messages` |
| 反馈 | `controller/FeedbackController` | MyFeedback | 提交/查看反馈；管理员回复后自动生成定向通知 |
| 通知 | `controller/NoticeController` | MyNotice、导航栏 | 系统广播+反馈回复统一列表，已读走 `notice_read`，导航栏未读红点 |
| 验证码 | `controller/VerificationController` + `service/VerificationService` | Register/ChangeInformation | 6 位验证码，10 分钟有效、一次性 |

### 5.2 管理员能力（`/admin/*` 页面，`src/views/` + `src/Admin.vue`）

| 能力域 | 后端入口 | 前端页面 | 说明 |
|---|---|---|---|
| 概览 | `controller/AdminStaticController` | Admin.vue | 全库计数（用户/文件/生成/反馈） |
| 用户管理 | `controller/AdminUserController` | AdminUser | 列表/重置密码为 666666/删除（级联）/统计 |
| 通知管理 | `controller/AdminNoticeController` | AdminNotice | 系统通知 CRUD（广播 target_user_id=NULL）+ 已读人数 |
| 系统监控 | `controller/AdminLogController` | AdminLog | API 统计/系统日志/健康概览/表状态 |
| 反馈处理 | `controller/FeedbackController`（管理员角色校验） | AdminFeedback | 列表/详情/回复/删除，回复联动写通知 |

> 模块/数据流/ER 详图见 `docs/architecture.md`；完整接口定义见 `docs/openapi.yaml`。

## 6. 配置要点

| 配置 | 位置 | 事实说明 |
|---|---|---|
| 环境变量 | `data/.env`（Java 启动时经 `application.yml` 自动导入，可用环境变量覆盖） | `JWT_SECRET`、SMTP_*（QQ 授权码）、`NSCC_*`（Qwen3.5）、`SILICONFLOW_*`（GLM-4.9）、`DEEPSEEK_*`，以及三个通道各自的 `*_ENABLED` 开关；详见 `spring-backend/README.md` §5 |
| 后端地址 | `src/config.js` 的 `API_BASE_URL` | 默认 `http://localhost:3000`；前端所有页面统一从这里取值 |
| 数据库连接 | `backend/.env` 的 `DB_HOST/DB_USER/DB_PASSWORD/DB_NAME` | 未设置时回退本地默认值 `localhost/root/000/X`（`application.yml`） |

## 7. 安全与错误处理要点

- JWT 鉴权：`security/JwtAuthenticationFilter` 验签并查库装配角色（延续「不信任 JWT 中角色声明」策略）；数据隔离按 `user_id = ?`
- 密码：BCrypt 哈希；规则「仅字母数字、6–16 位」（`AuthService` 校验）
- SQL：全部参数化查询
- AI 空回复兜底：Qwen/DeepSeek 返回空 content 时记录原始响应、写系统日志并返回 `502`（`service/ChatService` / `client/LlmClient`）
- 编译失败：返回 stderr、临时目录 `safeCleanup` 必清理
- ✅ 已加固（2026-09-07，详见 `docs/log.md`）：`/api/admin/*` 四个模块统一挂 `authenticateToken + requireAdmin`；PDF 移除 `/storage` 无鉴权静态托管，改 `GET /api/compile/:id/pdf` 归属校验流式返回；LaTeX 编译前危险序列校验；`db.js` 凭据改读 `.env`；JWT 去除兜底密钥（缺失启动即退出）；密码下限 6 位
- ⚠️ 运维注意：`.env` 含 SMTP/LLM 密钥须保密勿提交；`1.sql` 预置管理员哈希未经明文验证（重置密码统一 `666666` 见 `service/AdminUserService`）

## 8. 版本说明

| 版本 | 日期 | 说明 |
|---|---|---|
| v3.4 | 2026-09-13 | **目录整理**：清理残留垃圾（backend.log/texput.log/空目录等）；共享数据目录 `backend/` 改名为 `data/`（配置、DB `generation_path` 前缀 128 行、文档同步更新），语义更清晰 |
| v3.3 | 2026-09-13 | **Node 后端退役**：删除 `hello/backend/` 下全部 Node/Express 代码（app.js/routes/services 等），`backend/` 原地保留为共享运行时数据目录（`.env` + `uploads/` + `storage/`）；唯一后端为 `spring-backend/`；`conversations` 建表 DDL 归档至 `migrations/create_conversations_tables.sql`（详见 `docs/log.md`） |
| v3.2 | 2026-09-13 | **新增 Java 后端**：`spring-backend/`（Spring Boot 3.2 + MyBatis-Plus 3.5）全量重写 Node/Express 后端（13 路由 / 11 表 / JWT 鉴权 / AI 生成 / XeLaTeX 编译 / 上传 / 邮件 / 管理后台），响应统一 `{success,data,message}` 并同步适配前端；Node 后端保留待退役（详见 `docs/log.md` 2026-09-13） |
| v3.1 | 2026-09-07 | **全量 P0 安全加固 + 配置修复**：`/api/admin/*` 统一挂 `authenticateToken + requireAdmin`；PDF 移除无鉴权静态托管改归属校验流式返回（前端 Blob 预览）；LaTeX 编译前危险序列校验；`db.js` 凭据改读 `.env`（缺省回退本地默认）；JWT 去除兜底密钥（缺失启动即退出）；密码 6-16 位；修复 `.env` 旧占位 `DB_*` 导致的本地连库失败（详见 `docs/log.md` 2026-09-07） |
| v3.0 | 2026-09-05 | **精简重构为总入口**；修正与代码不一致处（如后端路由前缀实为 **13 个**，非 v2.0 所述 14 个）；详细 API/部署/架构内容迁至 docs/ 专项文档，避免多份重复维护 |
| v2.0 | 2026-07-16 | 旧版主文档（API 明细等已迁移，其「与旧文档差异」并入 `docs/architecture.md` §7） |

**预置管理员账号**：`admin123` / `666666`（`1.sql:14-20`）。

---
**文档版本**：3.1
**最后更新**：2026-09-07
**基准**：`hello/` 下实际源码与 SQL/迁移文件（行号引用同 `docs/architecture.md`）
