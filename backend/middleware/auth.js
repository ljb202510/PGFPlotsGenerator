// middleware/auth.js
const jwt = require('jsonwebtoken');
const dbModule = require('../db');
const db = dbModule.promisePool;

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

    const decoded = jwt.verify(token, process.env.JWT_SECRET || 'your-secret-key');
    
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

module.exports = {
  authenticateToken
};