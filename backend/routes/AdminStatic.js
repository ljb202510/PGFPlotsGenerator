// routes/AdminStatic.js
const express = require('express');
const router = express.Router();
const dbModule = require('../db');
const db = dbModule.promisePool;

// 获取系统统计数据的接口
router.get('/', async (req, res) => {
  try {
    // 获取用户数量
    const [usersResult] = await db.execute('SELECT COUNT(*) as count FROM users');
    const usersCount = usersResult[0].count;

    // 获取文件数量
    const [filesResult] = await db.execute('SELECT COUNT(*) as count FROM data_file');
    const filesCount = filesResult[0].count;

    // 获取生成记录数量
    const [generationsResult] = await db.execute('SELECT COUNT(*) as count FROM generation_history');
    const generationsCount = generationsResult[0].count;

    // 获取反馈数量
    const [feedbackResult] = await db.execute('SELECT COUNT(*) as count FROM feedback');
    const feedbackCount = feedbackResult[0].count;

    const stats = {
      users: usersCount,
      files: filesCount,
      generations: generationsCount,
      feedback: feedbackCount
    };
    
    res.json({
      success: true,
      data: stats,
      message: '统计数据获取成功'
    });
  } catch (error) {
    console.error('数据库查询错误:', error);
    res.status(500).json({
      success: false,
      message: '获取统计数据失败: ' + error.message
    });
  }
});

module.exports = router;