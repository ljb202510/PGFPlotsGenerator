// app.js
require('dotenv').config();
const express = require('express');
const cors = require('cors')
const path = require('path');
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
// 路由注册
app.use('/api/auth', authRoutes);
app.use('/api/chat', chatRoutes);
app.use('/api/feedback', feedbackRoutes);
app.use('/api/verification', verificationRoutes);
app.use('/api/datasets', datasetsRoutes);
app.use('/api/compile', compileRouter);
app.use('/api/history', historyRouter);
app.use('/api/notice', noticeRouter);
app.use('/api/admin/notices', adminNoticeRouter);
app.use('/api/admin/users', adminUserRouter);
app.use('/api/admin/log', adminLogRouter);
app.use('/api/admin/static', adminStaticRouter);
app.use('/api/conversations', conversationRoutes);
//静态文件服务，查看pdf
app.use('/storage', express.static(path.join(__dirname, 'storage')));

// 启动服务器
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`服务器运行在端口 ${PORT}`);
});