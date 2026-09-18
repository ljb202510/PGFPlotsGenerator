# PGFPlotsGenerator 部署运维手册

> **定位**：生产部署全流程 + 备份恢复 + 日志定位 + 故障排查。面向运维/部署执行者，快速实用版。
> **依据**：`.env`（配置键位）、`docs/log.md` 实战部署经验（2025-12 ~ 2026-01，华为云 + 宝塔/Debian）、`docs/architecture.md`（架构事实）。
> **适用 OS**：以 Debian/Ubuntu（宝塔面板）为主，本地/Windows 环节会单独标注。

---

## 1. 部署前置清单

| 项 | 要求 | 说明 |
|---|---|---|
| 服务器 OS | Debian/Ubuntu（实测宝塔在部分系统启动失败，**换 Debian 最快解决**，见 log.md） | 也可用宝塔多机管理/SSH |
| JDK | 17+（Spring Boot 后端，Maven 构建） | 后端运行必需 |
| Node.js | ≥ 16（前端 Vue CLI 5 构建） | 用宝塔 Node 版本管理或 nvm；仅服务前端构建 |
| MySQL | 5.7+（库名 `X`） | 宝塔面板内创建 |
| XeLaTeX | **完整/中等版 TeX Live（约 3G）**；apt 精简版约 1G 编译会缺包 | 见 §2.7 |
| 网络 | 服务器需可访问 NSCC(Qwen)、SiliconFlow(GLM)、（可选）DeepSeek API、Embedding 服务与 QQ SMTP | 均为公网 HTTPS |
| 前端 | `API_BASE_URL` 需指向后端实际地址 | `src/config.js`，见 §2.2 |
| 凭据 | SMTP 授权码、NSCC/SiliconFlow（可选 DeepSeek）API Key、Embedding Key | 存 `.env`，勿入库 |

> 本项目前后端同目录：前端根 `hello/`，后端 `hello/spring-backend/`；`hello/data/`（原 `backend/` 改名而来）为共享运行时数据目录（`.env` + `uploads/` + `storage/`，Java 仍在读写），请勿删除。

## 2. 生产部署全流程

### 2.1 准备代码并构建前端

```bash
# 本地（或 CI）：安装前端依赖并构建
cd hello
npm install
npm run build          # 产物在 hello/dist/
```

### 2.2 修改前端后端地址（重要）

`hello/src/config.js` 默认 `export const API_BASE_URL = 'http://localhost:3000'`（`src/config.js:2`）。**所有页面都从这里取后端地址**（曾因漏改某处导致部分请求仍指向 localhost，见 log.md）。部署前改为：

```js
export const API_BASE_URL = 'http://<服务器IP或域名>:3000'; // 或反代后的 https 域名地址
```

改完后**重新 `npm run build`**。

### 2.3 上传代码与后端构建

```bash
# 建议方式：rsync/scp 直接上传代码（排除 node_modules、spring-backend/target/）
scp -r hello user@server:/opt/pgfplots/

# 服务器上构建后端（需 JDK 17+ 与 Maven）
cd /opt/pgfplots/hello/spring-backend
mvn -DskipTests package          # 产物 target/pgfplots-backend-1.0.0.jar
```

> ⚠️ 前端依赖装根目录 `hello/package.json`（Node 仅服务前端构建）；后端为 `spring-backend/`（Maven + JDK 17+）。

### 2.4 配置环境变量

Java 后端启动时自动读取 `hello/spring-backend/.env`（`spring.config.import`），无需复制模板。按需填写：`JWT_SECRET`、`DB_HOST/DB_USER/DB_PASSWORD/DB_NAME`、`SMTP_USER/PASS/FROM`、`NSCC_API_KEY/URL`（主通道 Qwen3.5）、`SILICONFLOW_API_KEY/URL/MODEL`（降级第二层 GLM-4-9B）、`DEEPSEEK_API_KEY/URL`（第三层，`DEEPSEEK_ENABLED` 开关）、`EMBEDDING_API_KEY/URL/MODEL/DIM`（RAG 向量化）；启用 RAG 时另配 `RAG_ENABLED=true` 并在上线后执行 RagCli 的 seed 模式初始化模板向量库（Windows 即 `scripts/rag_seed.cmd`，Linux 用等价的 `java -jar ... --rag-cli=seed`）。各通道 `*_ENABLED` 开关与默认值详见 `spring-backend/README.md` §5。

> 数据库 `DB_*` 未设置时回退 `localhost/3306/root/000/X`；服务器部署请直接在 `.env` 填线上真实凭据，无需改代码。`JWT_SECRET` 缺失启动即失败（fail-fast）。

### 2.5 初始化数据库（库名 `X`）

在宝塔或命令行 MySQL 执行，顺序固定（`docs/architecture.md` §5.3）：

```bash
mysql -u root -p X < hello/migrations/000_init.sql                                       # 建库建表 + 预置管理员
mysql -u root -p X < hello/migrations/add_notice_feedback_columns.sql  # notice 加定向/反馈字段
mysql -u root -p X < hello/migrations/create_notice_read_table.sql     # notice_read 已读表
mysql -u root -p X < hello/migrations/create_conversations_tables.sql  # 建 conversations / conversation_messages
mysql -u root -p X < hello/migrations/alter_api_log_prompt_version.sql # api_log 加 prompt_version（批次1/A2）
mysql -u root -p X < hello/migrations/alter_api_log_duration_ms.sql    # api_log 加 duration_ms（批次2/O1 耗时拆解）
mysql -u root -p X < hello/migrations/alter_api_log_error_type.sql     # api_log 加 error_type（批次3/E2 失败归类）
mysql -u root -p X < hello/migrations/create_rag_vector.sql            # rag_vector 向量表（RAG）
mysql -u root -p X < hello/migrations/alter_rag_vector_quality.sql     # rag_vector 加 quality 分级列
mysql -u root -p X < hello/migrations/alter_data_file_data_name.sql    # data_file.data_name 扩至 50
```

> 全部执行完共 **12 张表**。

> 部署常踩坑（log.md）：`migrations/000_init.sql` 首行是 `DROP DATABASE IF EXISTS X;`，会**清空重建**，切勿在生产已有数据时直接执行；建议导出为纯建表语句或先备份。

### 2.6 配置数据库凭据

Java 后端读取 `.env` 的 `DB_HOST/DB_USER/DB_PASSWORD/DB_NAME`，未设置时回退本地默认 `localhost/3306/root/000/X`。

服务器上若库名/账号不同，在 `.env` 填写对应值即可（log.md 曾因数据库配置与宝塔建库不一致导致登录后接口 500）。

### 2.7 安装 XeLaTeX（Debian/Ubuntu）

```bash
# 完整可用（建议，约 3G）：先装精简验证，缺包再补
sudo apt update
sudo apt install texlive-base texlive-xetex texlive-latex-recommended texlive-latex-extra
sudo apt install texlive-fonts-recommended texlive-fonts-extra
sudo apt install texlive-lang-chinese
xelatex --version        # 验证
```

> 经验（log.md）：apt 精简版约 1G，实测编译经常**缺宏包报错**；最终改用完整/中等版（约 3G）才稳定。也可 `wget https://mirror.ctan.org/systems/texlive/tlnet/install-tl-unx.tar.gz` 装 TeX Live 2024+，但需较长时间与交互。

**中文字体（Linux 关键坑，log.md）**：`util/LatexCompiler` 的文档模板使用 `SimSun` + `Times New Roman`（Windows 字体）。Linux 没有这两款字体，需二选一：
- 安装可用的中文 CJK 字体（如 Noto CJK：`sudo apt install fonts-noto-cjk`）并修改 `util/LatexCompiler` 的 `\setCJKmainfont`/`\setmainfont`；
- 或将 Windows 字体文件（simsun.ttc/times.ttf）放到 `~/.fonts` 并 `fc-cache -f`，用 `fc-list :lang=zh` 验证。

> 另：Linux 上建议把 xelatex 相关路径改为绝对路径（log.md 经验），并确认 `data/` 目录对运行用户可读写（编译需写 `storage/generated_charts/` 临时目录）。

### 2.8 启动后端

```bash
cd hello/spring-backend
java -jar target/pgfplots-backend-1.0.0.jar
# 建议用 systemd / pm2（pm2 start "java -jar ..."）或宝塔「Java 项目」托管并设置开机自启
```

> ⚠️ `.env` 位于 `spring-backend/`（不放仓库根目录，避免前端 vue-cli 误读其中 `PORT`/`NODE_ENV`），在 `spring-backend/` 或仓库根下启动都会自动导入；存储目录默认值按 `spring-backend/` 为工作目录解析（`../data/...`），部署时仍建议以其为 cwd（或显式设置 `UPLOADS_DIR/HISTORY_DIR/CHARTS_DIR/DEBUG_DIR` 绝对路径）。

### 2.9 nginx 站点配置

前端是 history 路由 SPA，需要把静态 `dist/` + 反向代理 `/api` 到后端，并做刷新回退（PDF 走 `/api/compile/:id/pdf`，无需额外 location）：

```nginx
server {
    listen 80;
    server_name your-domain.com;          # 或 IP

    root /opt/pgfplots/hello/dist;        # 前端构建产物
    index index.html;

    # API 反向代理到 Java 后端
    location /api/ {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 90s;           # AI 生成/编译可能较慢
    }

    # 刷新不 404（history 路由回退到 index.html）
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

> log.md 经验：不加 `try_files` 时前端页面刷新/直达子路由会 404。
> HTTPS 建议在宝塔「网站 → SSL」申请证书后启用，并把 `config.js` 的 `API_BASE_URL` 改为 https 域名（或配 `/api` 同域反代后改为相对地址）。

### 2.10 上线验证清单

```bash
curl -I http://127.0.0.1:3000/api/auth/validate        # 后端存活（未带 token 返回 401 即正常）
curl http://127.0.0.1:3000/                             # nginx 是否转发
ps aux | grep pgfplots-backend                          # 后端进程存活
mysql -u root -p -e "USE X; SHOW TABLES;"               # 12 张表
```

浏览器验证：
- [ ] 打开站点 → 登录页正常（admin123/666666）
- [ ] 注册收邮件验证码（SMTP）
- [ ] 生成一个图表并「生成 PDF」（XeLaTeX）
- [ ] 上传一个数据集
- [ ] 多轮对话后刷新页面不 404
- [ ] 管理员登录 → 四个子页数据可加载
- [ ] 反馈 → 管理员回复 → 用户端收到通知

## 3. 备份与恢复

### 3.1 需备份的内容

| 内容 | 位置 | 备份频率建议 |
|---|---|---|
| MySQL 库 `X` | 数据库 | 每日 |
| 数据集文件 | `hello/data/uploads/` | 随数据上传 |
| 生成产物 PDF/JSON | `hello/data/storage/`（`history/` + `generated_charts/`） | 每周 |
| 配置 | `hello/spring-backend/.env` | 变更即备份 |

### 3.2 备份命令

```bash
# MySQL
mysqldump -u root -p X > /backup/pgfplots-$(date +%F).sql
# 文件目录（保留结构）
tar czf /backup/uploads.tar.gz -C hello/data uploads
tar czf /backup/storage.tar.gz -C hello/data storage
```

### 3.3 恢复步骤

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS X;"
mysql -u root -p X < /backup/pgfplots-2026-09-05.sql     # 恢复表数据
tar xzf /backup/uploads.tar.gz  -C hello/data
tar xzf /backup/storage.tar.gz  -C hello/data
重启后端进程（systemd/pm2 restart 或重新 `java -jar`）
```

## 4. 日志与监控

| 日志 | 位置/入口 | 用途 |
|---|---|---|
| 后端进程日志 | systemd/`pm2 logs`/宝塔「Java 项目」控制台 | 后端进程 stdout/stderr、启动报错 |
| TeX 编译日志 | 编译目录内 `*.log`（临时目录，成功后自动清理） | XeLaTeX 缺包/字体报错 |
| 系统日志表 | MySQL `system_log`（`util/SystemLogWriter` 写入，error 截断 500 字） | 服务端异常/越权告警 |
| API 调用日志 | MySQL `api_log`（ChatService 每次生成 success/failed 记录） | AI 调用成败/报错 |
| 前端构建 | `npm run build` 输出 | 编译期错误 |

管理员后台「系统监控」页面（`/admin/log`）可在线查看 `api-stats`、`system-logs`、`health-overview`、`tables-status`。

## 5. 故障排查速查表

> 以下条目均来自本项目实际部署/运行记录（docs/log.md）与代码事实，现象→原因→解法。

| # | 现象 | 原因 | 解决 |
|---|---|---|---|
| 1 | 后端启动即失败（fail-fast：`JWT_SECRET` 缺失） | `spring-backend/.env` 未配置，或启动 cwd 在仓库根与 `spring-backend/` 之外（找不到 `.env`） | 在 `spring-backend/.env` 填 `JWT_SECRET`（或以环境变量注入） |
| 2 | 前端部分页面/接口仍连不上后端，报网络错误 | `src/config.js` 里 `API_BASE_URL` 漏改/有的文件仍用反引号拼接不一致 | 全局替换为 `config.js` 统一地址，重新 `npm run build` |
| 3 | 能登录但业务接口 500「数据库无法连接」 | `.env` 中 `DB_*` 与服务器 MySQL 不一致（log.md：需与宝塔数据库统一 + 导入 migrations/000_init.sql） | 在 `.env` 填 host/user/password/database，重启后端进程 |
| 4 | 删除用户/关联数据报「外键依赖错误」 | `users` 被多表 FK 引用，未按代码级联删除路径操作 | 用 AdminUser 删除接口（代码内级联）；或先清子表 |
| 5 | 图表编译失败「缺宏包」/某 package not found | XeLaTeX 安装不完整（apt 约 1G 精简版） | 补装 texlive-latex-extra / texlive-fonts-extra / texlive-lang-chinese，或装完整 TeX Live（约 3G） |
| 6 | 编译后中文不显示/乱码 | Linux 无 `SimSun`/`Times New Roman` 字体 | 装 `fonts-noto-cjk` 或导入字体并改 `util/LatexCompiler` 文档模板的 `\setCJKmainfont`，`fc-cache -f` 后重启后端 |
| 7 | 前端刷新/直达子路由 404 | nginx 未配 history 回退 | `location / { try_files $uri $uri/ /index.html; }` |
| 8 | 改了代码重启后接口仍走旧逻辑 | 旧 3000 端口进程残留 | `lsof -i:3000`/`ss -ltnp` 查占用进程并清理，确保只有一个后端进程 |
| 9 | AI 回复为空（空气泡） | 模型空 content：推理/超长提示词耗尽 max_tokens（log.md 2026-09-03） | 已加兜底：Qwen 关闭 thinking + `max_tokens:8192`；空返回会得到 502「AI 返回内容为空」明确错误 |
| 10 | 邮箱验证码收不到 | SMTP 授权码错误 / 465 端口不通 / 发信被拒 | 核对 `.env` SMTP_*；QQ 邮箱需「授权码」非登录密码；`VerificationService` 启动时控制台有 `邮件服务已就绪` 提示 |
| 11 | 上传 >100MB 失败 | Spring multipart 限制 100MB（`application.yml`） | 前端可提示；需放宽则改后端限制并同步 nginx `client_max_body_size` |
| 12 | 管理员接口可被直接访问 | 曾因四个 `/api/admin/*` 模块未挂鉴权（2026-09-07 已修复） | 现由 `SecurityConfig` 对 `/api/admin/**` 统一要求 ADMIN 角色（查库装配）；若仍异常先确认已更新至最新代码并重启后端 |
| 13 | `system_log`/`api_log` 无数据 | 仅有 API 失败/异常才写；`AdminLog` 页有「添加测试日志」可验证链路 | 在管理端系统监控点「添加测试日志」再查表 |
| 14 | 本地启动登录报 `Access denied for user 'root'@'localhost'` | `.env` 中 `DB_*` 是旧模板占位（`your-*`）或与本地库不一致 | 把 `DB_PASSWORD/DB_NAME` 改为本机真实值（本地默认 `000`/`X`），重启后端 |

## 6. 上线检查清单（Checklist）

- [ ] `git status` 无 `.env`、无 `node_modules` 提交；`.env` 已加入 `.gitignore`
- [ ] `.env` 全部真实值，`JWT_SECRET` 已改随机
- [ ] `src/config.js` 指向线上地址并重新 build
- [ ] 数据库 12 张表齐全（含迁移）
- [ ] `.env` 的 `DB_*` 与线上库一致
- [ ] `xelatex --version` 通过；`fc-list :lang=zh` 有中文字体
- [ ] 后端进程自启已配置（systemd / pm2 / 宝塔「Java 项目」）
- [ ] nginx 已配 `/api` 反代与 `try_files`
- [ ] 走完 §2.10 浏览器验证清单

---
**文档版本**：1.3　**基准日期**：2026-09-18　**经验来源**：docs/log.md（2025-12 ~ 2026-09 部署记录）
