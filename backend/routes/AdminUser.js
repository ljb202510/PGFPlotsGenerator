const express = require('express');
const router = express.Router();
const dbModule = require('../db');
const db = dbModule.promisePool;
const { body, param, validationResult } = require('express-validator');
const bcrypt = require('bcrypt');

// 统一的响应格式函数
const formatResponse = (code, message, data = null) => {
  const success = code === 200;
  return {
    success,
    code,
    message,
    data
  };
};

// 获取用户列表
router.get('/', async (req, res) => {
  try {
    const {
      page = 1,
      pageSize = 10,
      keyword = ''
    } = req.query;

    const offset = (page - 1) * pageSize;
    
    // 构建查询条件
    let whereConditions = [];
    let queryParams = [];

    if (keyword) {
      whereConditions.push('(username LIKE ? OR email LIKE ?)');
      queryParams.push(`%${keyword}%`, `%${keyword}%`);
    }

    const whereClause = whereConditions.length > 0 
      ? `WHERE ${whereConditions.join(' AND ')}` 
      : '';

    // 获取总数
    const [countResult] = await db.query(
      `SELECT COUNT(*) as total FROM users ${whereClause}`,
      queryParams
    );
    const total = countResult[0].total;

    // 获取分页数据（排除密码字段）
    const [users] = await db.query(
      `SELECT user_id, username, email, role, register_time 
       FROM users 
       ${whereClause}
       ORDER BY register_time DESC 
       LIMIT ? OFFSET ?`,
      [...queryParams, parseInt(pageSize), offset]
    );

    res.json(formatResponse(200, '获取用户列表成功', {
      users: users,
      pagination: {
        page: parseInt(page),
        pageSize: parseInt(pageSize),
        total,
        totalPages: Math.ceil(total / pageSize)
      }
    }));
    
  } catch (error) {
    console.error('获取用户列表失败:', error);
    res.status(500).json(formatResponse(500, '服务器内部错误'));
  }
});

// 重置用户密码为666666
router.patch('/:id/reset-password', async (req, res) => {
  try {
    const userId = parseInt(req.params.id);
    
    if (isNaN(userId)) {
      return res.status(400).json(formatResponse(400, '用户ID必须是数字'));
    }
    
    // 检查用户是否存在
    const [existingUsers] = await db.query(
      'SELECT user_id, username, email FROM users WHERE user_id = ?',
      [userId]
    );

    if (existingUsers.length === 0) {
      return res.status(404).json(formatResponse(404, '用户不存在'));
    }

    // 使用bcrypt生成安全的哈希值
    const hashedPassword = await bcrypt.hash('666666', 10);
    
    // 更新密码
    await db.query(
      'UPDATE users SET password = ? WHERE user_id = ?',
      [hashedPassword, userId]
    );

    const user = existingUsers[0];

    res.json(formatResponse(200, '密码已重置为666666', {
      user_id: user.user_id,
      username: user.username,
      email: user.email
    }));
    
  } catch (error) {
    console.error('重置密码失败:', error);
    res.status(500).json(formatResponse(500, '服务器内部错误'));
  }
});

// 删除用户
router.delete('/:id',
  param('id').isInt().withMessage('用户ID必须是整数'),
  async (req, res) => {
    const connection = await db.getConnection(); // 获取数据库连接用于事务
    
    try {
      const errors = validationResult(req);
      if (!errors.isEmpty()) {
        return res.status(400).json(formatResponse(400, '参数错误', {
          errors: errors.array()
        }));
      }
      
      const userId = parseInt(req.params.id);
      
      // 开始事务
      await connection.beginTransaction();
      
      // 检查用户是否存在
      const [existingUsers] = await connection.query(
        'SELECT user_id, username, email FROM users WHERE user_id = ?',
        [userId]
      );

      if (existingUsers.length === 0) {
        await connection.rollback();
        return res.status(404).json(formatResponse(404, '用户不存在'));
      }
      
      // 检查是否为管理员自己（防止删除自己）
      // 实际应用中应从认证信息中获取当前用户ID
      const currentAdminId = req.user?.userId || 1; // 从认证信息获取
      if (userId === currentAdminId) {
        await connection.rollback();
        return res.status(400).json(formatResponse(400, '不能删除自己'));
      }
      
      // 按正确顺序删除关联数据
      try {
        // 1. 删除api_log记录
        await connection.query('DELETE FROM api_log WHERE user_id = ?', [userId]);
        
        // 2. 删除feedback记录
        await connection.query('DELETE FROM feedback WHERE user_id = ?', [userId]);
        
        // 3. 删除generation_history记录
        await connection.query('DELETE FROM generation_history WHERE user_id = ?', [userId]);
        
        // 4. 删除data_file记录
        await connection.query('DELETE FROM data_file WHERE user_id = ?', [userId]);
        
        // 5. 删除email_verification_codes记录
        const userEmail = existingUsers[0].email;
        await connection.query('DELETE FROM email_verification_codes WHERE email = ?', [userEmail]);
        
        // 6. 最后删除用户
        await connection.query('DELETE FROM users WHERE user_id = ?', [userId]);
        
        // 提交事务
        await connection.commit();
        
        const deletedUser = existingUsers[0];
        
        res.json(formatResponse(200, '用户删除成功', {
          user_id: deletedUser.user_id,
          username: deletedUser.username,
          email: deletedUser.email
        }));
        
      } catch (error) {
        await connection.rollback();
        throw error; // 重新抛出错误以便外层catch捕获
      }
      
    } catch (error) {
      // 如果已经rollback过，这里不需要再次rollback
      if (connection && !connection._rolledBack) {
        await connection.rollback();
      }
      
      console.error('删除用户失败:', error);
      
      // 处理外键约束错误
      if (error.code === 'ER_ROW_IS_REFERENCED_2') {
        res.status(400).json(formatResponse(400, '删除失败：用户有关联数据无法删除'));
      } else {
        res.status(500).json(formatResponse(500, '服务器内部错误'));
      }
    } finally {
      if (connection) {
        connection.release(); // 释放连接
      }
    }
  }
);

// 获取用户统计数据
router.get('/statistics/overview', async (req, res) => {
  try {
    // 获取用户总数和角色分布
    const [roleStats] = await db.query(`
      SELECT 
        role,
        COUNT(*) as count
      FROM users 
      GROUP BY role
    `);

    // 获取最近30天的注册统计
    const [recentStats] = await db.query(`
      SELECT 
        DATE(register_time) as date,
        COUNT(*) as count
      FROM users 
      WHERE register_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
      GROUP BY DATE(register_time)
      ORDER BY date DESC
    `);

    // 获取总统计
    const [summaryResult] = await db.query(`
      SELECT 
        COUNT(*) as total,
        COUNT(CASE WHEN role = 'admin' THEN 1 END) as admin_count,
        COUNT(CASE WHEN role = 'user' THEN 1 END) as user_count
      FROM users
    `);

    const total = summaryResult[0].total;
    const adminCount = summaryResult[0].admin_count;
    const userCount = summaryResult[0].user_count;

    res.json(formatResponse(200, '获取用户统计数据成功', {
      recent: recentStats,
      roleStats,
      summary: {
        total,
        admin_count: adminCount,
        user_count: userCount
      }
    }));
  } catch (error) {
    console.error('获取用户统计数据时出错:', error);
    res.status(500).json(formatResponse(500, '服务器内部错误'));
  }
});

module.exports = router;