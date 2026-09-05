# PGFPlotsGenerator 开发者指南

> **定位**：面向参与本项目开发的工程师（含 AI 编码助手）。覆盖本地环境搭建、代码结构导览、编码约定与二次开发流程，快速实用版。
> **前置阅读**：`docs/architecture.md`（模块/数据流/ER 详图，本文不重复堆砌）；`README.md`（API 明细）。
> **协作规则**：`.github/copilot-instructions.md`（AI 代理必须遵守的仓库指引）。

---

## 1. 本地环境搭建

### 1.1 前置依赖

| 依赖 | 版本建议 | 用途 |
|---|---|---|
| Node.js / npm | ≥ 16 | 前端构建 + 后端运行 |
| MySQL | 5.7+ | 库名 `X` |
| XeLaTeX（TeX Live） | 完整版 | 编译 PDF（本地 Windows 可装 TeX Live/TeXstudio，log.md：下载 arm 版会导致无法运行） |

### 1.2 初始化数据库（Windows 示例，密码见 `backend/db.js`）

```bash
cd hello
mysql -u root -p        # 输入密码 000
mysql> source 1.sql;                                    # 建库 X + 8 张表 + 预置管理员 admin123/666666
mysql> source migrations/add_notice_feedback_columns.sql;   # notice 定向/反馈字段迁移
mysql> source migrations/create_notice_read_table.sql;      # notice_read 表
mysql> exit;

cd hello/backend
node migrations/001_conversations.js    # 建 conversations / conversation_messages（依赖 db.js 连库）
```

> 提醒：`1.sql` 首行 `DROP DATABASE IF EXISTS X;` 会清库重建，仅首次/重建使用。

### 1.3 双终端启动

```bash
# 终端 A：前端（Vue CLI，默认 http://localhost:8080）
cd hello
npm install
npm run serve

# 终端 B：后端（Express，默认 :3000）
cd hello/backend
npm install
npm run dev        # nodemon 热重启（推荐开发）
# 或 npm run start（node app.js）
```

后端首次启动前：复制 `backend/.env.example` 为 `backend/.env` 并填 `JWT_SECRET`、`SMTP_*`、`DEEPSEEK_*`、`NSCC_*`（可先只填 JWT_SECRET 即可登录，发验证码/调用 AI 再补）。

### 1.4 验证

- 打开 `http://localhost:8080` → 注册/登录页正常
- 后端控制台输出 `服务器运行在端口 3000`
- 用 `admin123/666666` 管理员登录；用户走邮箱验证码注册（需配好 SMTP）

## 2. 代码结构导览

```
hello/
├── 1.sql                      # 基础建表（users/data_file/generation_history/api_log/feedback/system_log/email_verification_codes/notice）
├── migrations/                # SQL 迁移（notice 字段、notice_read）
├── docs/                      # 文档（README 补充：architecture / deployment / development / log）
├── public/  dist/             # 前端模板 / 构建产物
├── src/                       # ★ 前端 Vue 3 SPA
│   ├── main.js                # 入口：ElementPlus/Vuex/router/全局 UI 组件注册 + ResizeObserver 防抖补丁
│   ├── config.js              # API_BASE_URL（前后端地址的唯一事实源）
│   ├── App.vue                # 布局外壳：按登录态渲染 TheAuth / 用户主区 / 管理员主区
│   ├── router/index.js        # 14 条平级路由（懒加载；无全局守卫）
│   ├── store/index.js         # Vuex：currentUser/isAuthenticated/unreadCount
│   ├── components/            # TheAuth、Login/Register/AdminLogin、Common/Admin Navbar+Sidebar、ui/ 通用组件
│   └── views/                 # ChartGenerator、MyHistory、DataUpload、MyFeedback、MyNotice、ChangeInformation、Admin* 共 10 页
└── backend/                   # ★ 后端 Express
    ├── app.js                 # 入口：挂载 13 个路由前缀 + /storage 静态
    ├── db.js                  # mysql2/promise 连接池（硬编码凭据），导出 { promisePool }
    ├── .env(.example)         # 环境变量（勿提交 .env）
    ├── middleware/auth.js     # JWT 校验 authenticateToken
    ├── routes/                # 13 个路由文件（auth/chat/compile/history/datasets/conversations/feedback/notice/verification/Admin*）
    ├── services/              # 非 HTTP 业务（verificationService.js 邮件+验证码）
    ├── utils/systemLog.js     # writeSystemLog 写 system_log（副作用，不抛异常）
    ├── migrations/            # 001_conversations.js（两对话表）
    ├── uploads/               # 数据集文件（multer）
    └── storage/               # history/{uid}/{id}.json、generated_charts/user{uid}/hist{id}.pdf（/storage 托管）
```

对应模块/路由/数据流细节见 `docs/architecture.md` §3–§5。

## 3. 编码规范与约定

> 后端 `backend/package.json` 配置了 **eslint-config-google**；前端走 Vue CLI ESLint（`npm run lint`）。

### 3.1 通用约定

- 全库为**参数化查询**（`?` 占位），禁止字符串拼接 SQL（防注入）。
- **数据隔离**：所有按用户查询/删除必须带 `user_id = ?` 条件；越权一律返回 404/403。
- 业务代码中的异常/越权统一调 `writeSystemLog(status, message)`（`backend/utils/systemLog.js`）记录，message 会被截断 500 字；该函数只作为副作用，失败不影响主流程。
- 密码规则（`routes/auth.js` `validatePassword`）：**仅字母数字、长度 1–8 位**；存取用 bcryptjs。
- 响应风格注意（前后端分别处理两种）：部分接口 `{ success, data?, message }`；部分 `{ code, message, data }`（如 notice.js、Admin*）。**新增接口建议统一 `{ success, data, message }`**。

### 3.2 后端约定

- **路由**：新增路由文件 `backend/routes/xxx.js`，在 `backend/app.js` 用 `app.use('/api/xxx', require('./routes/xxx'))` 挂载（`.github/copilot-instructions.md`）。
- **业务逻辑**：涉及 DB/外部服务的逻辑放 `backend/services/`（参照 `verificationService.js`），路由文件保持薄。
- **DB 访问**：`const db = require('../db').promisePool;` 然后 `await db.query(sql, params)`。
- **鉴权**：需要登录的接口挂 `authenticateToken`（`middleware/auth.js`）；管理员校验：反馈模块用 `feedback.js` 内 `checkAdmin`（查库 role），`Admin*` 模块当前无鉴权（生产需自行加固）。
- 删除级联等跨表操作使用**事务**（`getConnection + beginTransaction + commit/rollback + release`，参照 `history.js:253-292`、`conversations.js:90-111`）。
- 编译等外部命令用 `util.promisify(exec)` 并设 timeout（参照 `compile.js` 30s）。

### 3.3 前端约定

- **组件/页面**：路由懒加载 `() => import('../views/SomeView.vue')`（`.github/copilot-instructions.md`）；页面放 `views/`、复用组件放 `components/`（通用 UI 放 `components/ui/`）。
- **状态/认证**：token 存 `localStorage` 或 `sessionStorage`（以 token 存在与否二选一）；user 经 Vuex `SET_USER` 持久化。取 token 模式参照 `ChartGenerator.vue` 的 `getAuthToken()`。
- **API 基址**：一律从 `src/config.js` 引 `API_BASE_URL`（勿再硬编码地址/忘写反引号拼接——曾因此导致联调失败）。
- **样式**：全局设计令牌在 `src/styles/tokens.css`（`--brand`/`--bg-*`/`--radius-*` 等），组件用变量，勿散点硬编码色值。
- 图标用 `@element-plus/icons-vue`，不用 emoji/第三方图标库。

## 4. 二次开发：新增一个功能/接口（示例：新增「收藏图表」）

### 4.1 后端（5 步）

1. **建表/迁移**：给 `generation_history` 加列，写 `migrations/add_favorite_column.sql`（带存在性判断，参照 `add_notice_feedback_columns.sql`）并执行；或新建表。
2. **写路由文件** `backend/routes/favorite.js`：

```js
// routes/favorite.js —— 收藏功能示例
const express = require('express');
const router = express.Router();
const db = require('../db').promisePool;
const { authenticateToken } = require('../middleware/auth');
const { writeSystemLog } = require('../utils/systemLog');

// 收藏/取消收藏某条历史
router.put('/:historyId', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;
    const historyId = parseInt(req.params.historyId);
    const isFavorite = req.body.isFavorite ? 1 : 0;
    const [result] = await db.query(
      'UPDATE generation_history SET is_favorite = ? WHERE history_id = ? AND user_id = ?',
      [isFavorite, historyId, userId]
    );
    if (result.affectedRows === 0) {
      return res.status(404).json({ success: false, message: '历史不存在或无权操作' });
    }
    res.json({ success: true, message: '已更新收藏状态' });
  } catch (error) {
    await writeSystemLog('error', `[FAVORITE] 更新失败: ${error.message}`);
    res.status(500).json({ success: false, message: '服务器错误' });
  }
});

module.exports = router;
```

3. **挂载**：在 `backend/app.js` 引入并注册：

```js
// app.js 追加
const favoriteRouter = require('./routes/favorite');
app.use('/api/favorite', favoriteRouter);
```

4. （若跨表/多步）在 services 或事务内实现。
5. 验证：`npm run lint`；用 curl/Postman 带 Bearer token 调 `PUT /api/favorite/1`。

### 4.2 前端（4 步）

1. `src/views/MyHistory.vue`（或 ChartGenerator.vue）按现有模式调用：

```js
import axios from 'axios';
import { API_BASE_URL } from '@/config';

async function toggleFavorite(historyId, isFavorite) {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token');
  const { data } = await axios.put(`${API_BASE_URL}/api/favorite/${historyId}`,
    { isFavorite },
    { headers: { Authorization: `Bearer ${token}` } }
  );
  if (!data.success) throw new Error(data.message);
}
```

2. 需要跨页面共享则加 Vuex mutation/action（`src/store/index.js`）。
3. UI 组件复用 `components/ui/`（AppButton/EmptyState/AppSpinner 等）与 Element Plus。
4. 验证：页面操作 + 检查库中 `is_favorite` 更新、错误提示可用。

## 5. 常用命令清单

```bash
# 后端
cd hello/backend && npm run dev        # nodemon 开发
cd hello/backend && npm run start      # node app.js
cd hello/backend && npm run lint       # google-style 检查
# 前端
cd hello && npm run serve              # 开发服务器 :8080
cd hello && npm run build              # 产物 dist/
cd hello && npm run lint
# 数据库
mysql -u root -p X < 1.sql             # 重建（危险，会 DROP）
# 编译验证（手动）
cd <temp> && xelatex -interaction=nonstopmode test.tex
```

## 6. 常见开发易错点

- 改 `db.js`/`.env`/`config.js` 后需重启对应进程（nodemon 自动重启后端，前端改 config 需刷新）。
- 新增表后忘执行 `backend/migrations/001_conversations.js` 或迁移 SQL，接口会报「table doesn't exist」。
- `uploads`、`storage` 目录不存在时 multer/编译会失败——后端已自动创建部分目录（`datasets.js:13-15`、`compile.js:117-119`），若权限不足需手工 mkdir。
- 删除用户/数据走 AdminUser/MyHistory 接口（内部级联），避免直接 SQL 触发外键错误。
- 对话持久化只有传 `conversation_id` 才会写入（`chat.js:451`）；纯单轮生成不会进 `conversation_messages`。

---
**文档版本**：1.0　**基准日期**：2026-09-05　**交叉引用**：docs/architecture.md、README.md、.github/copilot-instructions.md
