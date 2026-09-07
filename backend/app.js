// app.js
require('dotenv').config();

// 启动前校验关键配置：JWT_SECRET 不允许缺失或用默认值兜底（缺失即 fail-fast，防伪造 token）
if (!process.env.JWT_SECRET) {
  console.error('缺少环境变量 JWT_SECRET：请复制 backend/.env.example 为 .env 并填写后重试。');
  process.exit(1);
}

const express = require('express');
const cors = require('cors')
const app = express();


app.use(cors())// 使用中间件解决跨域问题
app.use(express.json());

// 路由引入
const authRoutes = require('./routes/auth');
const chatRoutes = require('./routes/chat');
const feedbackRoutes = require('./routes/feedback');
const verificationRoutes = require('./routes/verification');
const datasetsRoutes = require('./routes/datasets');
const compileRouter = require('./routes/compile'); 
const historyRouter = require('./routes/history');
const noticeRouter = require('./routes/notice');
const adminNoticeRouter = require('./routes/AdminNotice');
const adminUserRouter = require('./routes/AdminUser');
const adminLogRouter = require('./routes/AdminLog');
const adminStaticRouter = require('./routes/AdminStatic');
const conversationRoutes = require('./routes/conversations');
const { authenticateToken, requireAdmin } = require('./middleware/auth');
// 路由注册
app.use('/api/auth', authRoutes);
app.use('/api/chat', chatRoutes);
app.use('/api/feedback', feedbackRoutes);
app.use('/api/verification', verificationRoutes);
app.use('/api/datasets', datasetsRoutes);
app.use('/api/compile', compileRouter);
app.use('/api/history', historyRouter);
app.use('/api/notice', noticeRouter);
// 管理员接口统一挂 JWT + 管理员角色双重鉴权
app.use('/api/admin/notices', authenticateToken, requireAdmin, adminNoticeRouter);
app.use('/api/admin/users', authenticateToken, requireAdmin, adminUserRouter);
app.use('/api/admin/log', authenticateToken, requireAdmin, adminLogRouter);
app.use('/api/admin/static', authenticateToken, requireAdmin, adminStaticRouter);
app.use('/api/conversations', conversationRoutes);

// 启动服务器
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`服务器运行在端口 ${PORT}`);
});