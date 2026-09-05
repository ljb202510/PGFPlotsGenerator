# PGFPlotsGenerator 部署运维手册

> **定位**：生产部署全流程 + 备份恢复 + 日志定位 + 故障排查。面向运维/部署执行者，快速实用版。
> **依据**：`backend/.env.example`（配置键位）、`docs/log.md` 实战部署经验（2025-12 ~ 2026-01，华为云 + 宝塔/Debian）、`docs/architecture.md`（架构事实）。
> **适用 OS**：以 Debian/Ubuntu（宝塔面板）为主，本地/Windows 环节会单独标注。

---

## 1. 部署前置清单

| 项 | 要求 | 说明 |
|---|---|---|
| 服务器 OS | Debian/Ubuntu（实测宝塔在部分系统启动失败，**换 Debian 最快解决**，见 log.md） | 也可用宝塔多机管理/SSH |
| Node.js | ≥ 16（后端 Express + 前端 Vue CLI 5 构建） | 用宝塔 Node 版本管理或 nvm |
| MySQL | 5.7+（库名 `X`） | 宝塔面板内创建 |
| XeLaTeX | **完整/中等版 TeX Live（约 3G）**；apt 精简版约 1G 编译会缺包 | 见 §2.7 |
| 网络 | 服务器需可访问 DeepSeek API、NSCC(Qwen)、QQ SMTP | 三家均为公网 HTTPS |
| 前端 | `API_BASE_URL` 需指向后端实际地址 | `src/config.js`，见 §2.2 |
| 凭据 | SMTP 授权码、DeepSeek/NSCC API Key | 存 `backend/.env`，勿入库 |

> 本项目前后端同目录：前端根 `hello/`，后端 `hello/backend/`。

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

### 2.3 上传代码与后端依赖

```bash
# 建议方式 A：rsync/scp 直接上传代码（排除 node_modules）
scp -r hello user@server:/opt/pgfplots/

# 方式 B（npm 网络不稳时的经验做法，log.md）：本地装好依赖后整体上传 node_modules，
# 服务器上再补装：cd backend && npm install --registry=https://registry.npmmirror.com
```

> ⚠️ 后端 `backend/package.json` **未声明 `cors`、`multer`** 两个运行时依赖（`app.js`/`datasets.js` 直接 require）。务必保证 `backend/node_modules` 完整，否则后端启动即报「找不到模块」。
> ⚠️ 前端依赖装根目录 `hello/package.json`，后端依赖装 `hello/backend/package.json`，两边都要安装。

### 2.4 配置环境变量

```bash
cd hello/backend
cp .env.example .env     # 模板键位与本文件一致，值是占位符
```

按模板逐项填写：`JWT_SECRET`、`SMTP_USER/PASS/FROM`、`DEEPSEEK_API_KEY/URL`、`NSCC_API_KEY/URL`。键的用途与读取模块见 `.env.example` 内注释。

> 数据库相关 `DB_*` 目前是占位备用——后端实际读 `db.js` 硬编码连接（见 2.6），改库凭据必须同步改 `db.js`。

### 2.5 初始化数据库（库名 `X`）

在宝塔或命令行 MySQL 执行，顺序固定（`docs/architecture.md` §5.3）：

```bash
mysql -u root -p X < hello/1.sql                                      # 建库建表 + 预置管理员
mysql -u root -p X < hello/migrations/add_notice_feedback_columns.sql  # notice 加定向/反馈字段
mysql -u root -p X < hello/migrations/create_notice_read_table.sql     # notice_read 已读表

cd hello/backend
node migrations/001_conversations.js     # 建 conversations / conversation_messages
```

> 部署常踩坑（log.md）：`1.sql` 首行是 `DROP DATABASE IF EXISTS X;`，会**清空重建**，切勿在生产已有数据时直接执行；建议导出为纯建表语句或先备份。

### 2.6 同步 db.js 数据库凭据

`hello/backend/db.js` 顶部硬编码：

```js
host: 'localhost', user: 'root', password: '000', database: 'X'
```

服务器上若库名/账号不同，直接改这里（log.md 曾因 db.js 与宝塔建库不一致导致登录后接口 500）。文件底部注释有「服务器版本」示例可参考。

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

**中文字体（Linux 关键坑，log.md）**：`compile.js:236-255` 使用 `SimSun` + `Times New Roman`（Windows 字体）。Linux 没有这两款字体，需二选一：
- 安装可用的中文 CJK 字体（如 Noto CJK：`sudo apt install fonts-noto-cjk`）并修改 `backend/routes/compile.js` 的 `\setCJKmainfont`/`\setmainfont`；
- 或将 Windows 字体文件（simsun.ttc/times.ttf）放到 `~/.fonts` 并 `fc-cache -f`，用 `fc-list :lang=zh` 验证。

> 另：Linux 上建议把 xelatex 相关路径改为绝对路径（log.md 经验），并确认 `backend/` 目录对运行用户可读写（编译需写 `storage/generated_charts/` 临时目录）。

### 2.8 启动后端（pm2）

```bash
cd hello/backend
npm install                  # 若尚未安装
pm2 start app.js --name pgfplots-backend
pm2 save && pm2 startup      # 开机自启
pm2 logs pgfplots-backend    # 查看日志
```

宝塔面板也可用「Node 项目 → 添加 Node 项目」托管（log.md 中面板方式曾反复失败，最终用 pm2/终端 `node app.js` 跑通；`backend/package.json` scripts 有 `npm run dev`(nodemon)，生产用 `npm start` 或 pm2 直接 `app.js`）。

### 2.9 nginx 站点配置

前端是 history 路由 SPA，需要把静态 `dist/` + 反向代理 `/api`、`/storage` 到后端，并做刷新回退：

```nginx
server {
    listen 80;
    server_name your-domain.com;          # 或 IP

    root /opt/pgfplots/hello/dist;        # 前端构建产物
    index index.html;

    # API 反向代理到 Node
    location /api/ {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 90s;           # AI 生成/编译可能较慢
    }

    # PDF 静态文件（compile 产物）转发后端 /storage
    location /storage/ {
        proxy_pass http://127.0.0.1:3000;
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
pm2 status pgfplots-backend
mysql -u root -p -e "USE X; SHOW TABLES;"               # 11 张表
```

浏览器验证：
- [ ] 打开站点 → 登录页正常（admin123/666666）
- [ ] 注册收邮件验证码（SMTP）
- [ ] 生成一个图表并「生成 PDF」（XeLaTeX）
- [ ] 上传一个数据集（multer）
- [ ] 多轮对话后刷新页面不 404
- [ ] 管理员登录 → 四个子页数据可加载
- [ ] 反馈 → 管理员回复 → 用户端收到通知

## 3. 备份与恢复

### 3.1 需备份的内容

| 内容 | 位置 | 备份频率建议 |
|---|---|---|
| MySQL 库 `X` | 数据库 | 每日 |
| 数据集文件 | `hello/backend/uploads/` | 随数据上传 |
| 生成产物 PDF/JSON | `hello/backend/storage/`（`history/` + `generated_charts/`） | 每周 |
| 配置 | `hello/backend/.env` | 变更即备份 |

### 3.2 备份命令

```bash
# MySQL
mysqldump -u root -p X > /backup/pgfplots-$(date +%F).sql
# 文件目录（保留结构）
tar czf /backup/uploads.tar.gz -C hello/backend uploads
tar czf /backup/storage.tar.gz -C hello/backend storage
```

### 3.3 恢复步骤

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS X;"
mysql -u root -p X < /backup/pgfplots-2026-09-05.sql     # 恢复表数据
tar xzf /backup/uploads.tar.gz  -C hello/backend
tar xzf /backup/storage.tar.gz  -C hello/backend
pm2 restart pgfplots-backend
```

## 4. 日志与监控

| 日志 | 位置/入口 | 用途 |
|---|---|---|
| pm2 日志 | `pm2 logs pgfplots-backend` | 后端进程 stdout/stderr、启动报错 |
| 后端运行日志 | `hello/backend/backend.log` | 老版本控制台输出残留，可辅助排查 |
| TeX 编译日志 | `hello/backend/texput.log`（编译目录内 `*.log`） | XeLaTeX 缺包/字体报错 |
| 系统日志表 | MySQL `system_log`（`backend/utils/systemLog.js` 写入，error 截断 500 字） | 服务端异常/越权告警 |
| API 调用日志 | MySQL `api_log`（chat.js 每次生成 success/failed 记录） | AI 调用成败/报错 |
| 前端构建 | `npm run build` 输出 | 编译期错误 |

管理员后台「系统监控」页面（`/admin/log`）可在线查看 `api-stats`、`system-logs`、`health-overview`、`tables-status`。

## 5. 故障排查速查表

> 以下条目均来自本项目实际部署/运行记录（docs/log.md）与代码事实，现象→原因→解法。

| # | 现象 | 原因 | 解决 |
|---|---|---|---|
| 1 | 后端启动报「Cannot find module 'cors'/'multer'」 | `backend/package.json` 未声明这两个运行时依赖，node_modules 不完整 | 本地装好后整体上传 node_modules；或服务器 `npm install` 后确认两包存在 |
| 2 | 前端部分页面/接口仍连不上后端，报网络错误 | `src/config.js` 里 `API_BASE_URL` 漏改/有的文件仍用反引号拼接不一致 | 全局替换为 `config.js` 统一地址，重新 `npm run build` |
| 3 | 能登录但业务接口 500「数据库无法连接」 | `db.js` 硬编码凭据与服务器 MySQL 不一致（log.md：需与宝塔数据库统一 + 导入 1.sql） | 改 `db.js` 的 host/user/password/database，重启 pm2 |
| 4 | 删除用户/关联数据报「外键依赖错误」 | `users` 被多表 FK 引用，未按代码级联删除路径操作 | 用 AdminUser 删除接口（代码内级联）；或先清子表 |
| 5 | 图表编译失败「缺宏包」/某 package not found | XeLaTeX 安装不完整（apt 约 1G 精简版） | 补装 texlive-latex-extra / texlive-fonts-extra / texlive-lang-chinese，或装完整 TeX Live（约 3G） |
| 6 | 编译后中文不显示/乱码 | Linux 无 `SimSun`/`Times New Roman` 字体 | 装 `fonts-noto-cjk` 或导入字体并改 `compile.js` 的 `\setCJKmainfont`，`fc-cache -f` 后重启 |
| 7 | 前端刷新/直达子路由 404 | nginx 未配 history 回退 | `location / { try_files $uri $uri/ /index.html; }` |
| 8 | 改了代码重启后接口仍走旧逻辑 | 旧 3000 端口进程残留 | `lsof -i:3000`/`ss -ltnp` 查占用进程并清理，确保只有一个 `app.js` |
| 9 | AI 回复为空（空气泡） | 模型空 content：推理/超长提示词耗尽 max_tokens（log.md 2026-09-03） | 已加兜底：Qwen 关闭 thinking + `max_tokens:8192`；空返回会得到 502「AI 返回内容为空」明确错误 |
| 10 | 邮箱验证码收不到 | SMTP 授权码错误 / 465 端口不通 / 发信被拒 | 核对 `.env` SMTP_*；QQ 邮箱需「授权码」非登录密码；`verificationService.js` 启动时控制台有 `邮件服务已就绪` 提示 |
| 11 | 上传 >100MB 失败 | multer `limits.fileSize` 100MB（datasets.js:33） | 前端可提示；需放宽则改后端限制并同步 nginx `client_max_body_size` |
| 12 | 管理员接口可被直接访问 | 四个 `/api/admin/*` 模块未挂 JWT 鉴权中间件（architecture.md §8 事实） | 生产建议补齐服务端鉴权或限制来源 IP/内网访问 |
| 13 | `system_log`/`api_log` 无数据 | 仅有 API 失败/异常才写；`AdminLog` 页有「添加测试日志」可验证链路 | 在管理端系统监控点「添加测试日志」再查表 |

## 6. 上线检查清单（Checklist）

- [ ] `git status` 无 `.env`、无 `node_modules` 提交；`.env` 已加入 `.gitignore`
- [ ] `backend/.env` 全部真实值，`JWT_SECRET` 已改随机
- [ ] `src/config.js` 指向线上地址并重新 build
- [ ] 数据库 11 张表齐全（含迁移）
- [ ] `db.js` 凭据与线上库一致
- [ ] `xelatex --version` 通过；`fc-list :lang=zh` 有中文字体
- [ ] pm2 自启已配置（`pm2 save && pm2 startup`）
- [ ] nginx 已配 `/api`、`/storage` 反代与 `try_files`
- [ ] 走完 §2.10 浏览器验证清单

---
**文档版本**：1.0　**基准日期**：2026-09-05　**经验来源**：docs/log.md（2025-12 ~ 2026-09 部署记录）
