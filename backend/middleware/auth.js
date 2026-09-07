// middleware/auth.js
const jwt = require('jsonwebtoken');
const dbModule = require('../db');
const db = dbModule.promisePool;
const { writeSystemLog } = require('../utils/systemLog');

// JWT验证中间件
const authenticateToken = async (req, res, next) => {
  try {
    const token = req.headers.authorization?.replace('Bearer ', '');
    
    if (!token) {
      return res.status(401).json({
        success: false,
        message: '未提供token'
      });
    }

    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    
    // 从数据库查找用户
    const [users] = await db.query('SELECT user_id, username, email FROM users WHERE user_id = ?', [decoded.userId]);
    
    if (users.length === 0) {
      return res.status(401).json({
        success: false,
        message: '用户不存在'
      });
    }

    req.user = users[0];
    next();
  } catch (error) {
    res.status(401).json({
      success: false,
      message: 'token无效'
    });
  }
};

// 管理员权限校验（查库校验 role，不信任 JWT payload 中的角色声明，须置于 authenticateToken 之后使用）
const requireAdmin = async (req, res, next) => {
  try {
    const userId = req.user?.user_id;

    if (!userId) {
      return res.status(401).json({
        success: false,
        message: '用户未认证'
      });
    }

    // 查询用户角色
    const [rows] = await db.query('SELECT role FROM users WHERE user_id = ?', [userId]);

    if (rows.length === 0 || rows[0].role !== 'admin') {
      await writeSystemLog('warning', `[AUTH] 权限不足-非管理员访问: 用户 ${userId}`);
      return res.status(403).json({
        success: false,
        message: '权限不足，需要管理员权限'
      });
    }

    next();
  } catch (error) {
    console.error('权限检查错误:', error);
    await writeSystemLog('error', `[AUTH] 权限检查异常: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器错误: ' + error.message
    });
  }
};

module.exports = {
  authenticateToken,
  requireAdmin
};