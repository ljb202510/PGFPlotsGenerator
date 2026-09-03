// routes/feedback.js
const express = require('express');
const router = express.Router();
const dbModule = require('../db');
const db = dbModule.promisePool;
const { authenticateToken } = require('../middleware/auth');
const { writeSystemLog } = require('../utils/systemLog');

// 管理员权限检查中间件
const checkAdmin = async (req, res, next) => {
  try {
    const userId = req.user?.user_id;
    
    if (!userId) {
      return res.status(401).json({
        success: false,
        message: '用户未认证'
      });
    }
    
    // 查询用户角色
    const query = 'SELECT role FROM users WHERE user_id = ?';
    const [rows] = await db.execute(query, [userId]);
    
    if (rows.length === 0 || rows[0].role !== 'admin') {
      await writeSystemLog('warning', `[FEEDBACK] 权限不足-非管理员访问: 用户 ${userId}`);
      return res.status(403).json({
        success: false,
        message: '权限不足，需要管理员权限'
      });
    }
    
    next();
  } catch (error) {
    console.error('权限检查错误:', error);
    await writeSystemLog('error', `[FEEDBACK] 权限检查异常: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器错误: ' + error.message
    });
  }
};

//用户提交反馈
router.post('/', authenticateToken, async (req, res) => {
  try {
    const { type, content } = req.body;
    const user_id = req.user.user_id;
    
    // 基本验证
    if (!type || !content) {
      await writeSystemLog('warning', `[FEEDBACK] 提交反馈校验失败-类型和内容不能为空 (用户 ${req.user?.user_id})`);
      return res.status(400).json({
        success: false,
        message: '反馈类型和内容不能为空'
      });
    }
    
    if (content.length > 100) {
      await writeSystemLog('warning', `[FEEDBACK] 提交反馈校验失败-内容超过100字 (用户 ${req.user?.user_id})`);
      return res.status(400).json({
        success: false,
        message: '反馈内容不能超过100字'
      });
    }
    
    // 验证反馈类型
    const validTypes = ['suggestion', 'ui', 'bug', 'other'];
    if (!validTypes.includes(type)) {
      await writeSystemLog('warning', `[FEEDBACK] 提交反馈校验失败-无效类型: ${type} (用户 ${req.user?.user_id})`);
      return res.status(400).json({
        success: false,
        message: '无效的反馈类型'
      });
    }
    
    // 插入反馈
    const query = `INSERT INTO feedback (user_id, type, content) VALUES (?, ?, ?)`;
    const [result] = await db.execute(query, [user_id, type, content]);
    
    // 简单返回成功信息
    res.status(201).json({
      success: true,
      data: {
        feedback_id: result.insertId
      },
      message: '反馈提交成功'
    });
    
  } catch (error) {
    console.error('提交反馈错误:', error);
    await writeSystemLog('error', `[FEEDBACK] 提交反馈失败: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器错误: ' + error.message
    });
  }
});

// 用户获取自己的反馈列表 - 需要认证
router.get('/user/my-feedbacks', authenticateToken, async (req, res) => {
  try {
    const user_id = req.user.user_id;
    const { page = 1, limit = 10 } = req.query;
    
    // 参数处理 - 确保转换为数字类型
    const pageNum = Math.max(1, parseInt(page) || 1);
    const limitNum = Math.min(100, Math.max(1, parseInt(limit) || 10));
    const offset = (pageNum - 1) * limitNum;

    // 修改处1: 使用 db.query() 替代 db.execute()
    const query = `
      SELECT 
        feedback_id,
        type,
        content,
        answer,
        feedback_time,
        answer_time,
        CASE 
          WHEN answer IS NOT NULL THEN '已回复'
          ELSE '待回复'
        END as status
      FROM feedback 
      WHERE user_id = ?
      ORDER BY feedback_time DESC 
      LIMIT ? OFFSET ?
    `;
    
    // 修改处2: 将 db.execute() 改为 db.query()
    const [feedbacks] = await db.query(query, [user_id, limitNum, offset]);

    // 修改处3: 总数查询也使用 db.query() 保持一致性
    const countQuery = 'SELECT COUNT(*) as total FROM feedback WHERE user_id = ?';
    const [countResult] = await db.query(countQuery, [user_id]);
    const total = countResult[0].total;

    res.json({
      success: true,
      data: feedbacks,
      pagination: {
        page: pageNum,
        limit: limitNum,
        total,
        pages: Math.ceil(total / limitNum)
      },
      message: '获取反馈列表成功'
    });
    
  } catch (error) {
    console.error('获取用户反馈列表错误:', error);
    await writeSystemLog('error', `[FEEDBACK] 获取用户反馈列表失败: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器内部错误: ' + error.message
    });
  }
});

// 管理员获取所有反馈 - 需要认证和管理员权限
router.get('/', authenticateToken, checkAdmin, async (req, res) => {
  try {
    const { page = 1, limit = 10, type, startDate, endDate } = req.query;
    
    // 参数处理
    const pageNum = Math.max(1, parseInt(page) || 1);
    const limitNum = Math.min(100, Math.max(1, parseInt(limit) || 10));
    const offset = (pageNum - 1) * limitNum;

    // 基础查询
    let query = `
      SELECT f.*, u.username 
      FROM feedback f 
      JOIN users u ON f.user_id = u.user_id 
    `;
    let countQuery = 'SELECT COUNT(*) as total FROM feedback f';
    
    const whereClauses = [];
    const params = [];
    const countParams = [];
    
    // 添加类型筛选
    if (type) {
      whereClauses.push('f.type = ?');
      params.push(type);
      countParams.push(type);
    }
    
    // 添加时间范围筛选
    if (startDate && endDate) {
      whereClauses.push('f.feedback_time BETWEEN ? AND ?');
      params.push(startDate, endDate + ' 23:59:59');
      countParams.push(startDate, endDate + ' 23:59:59');
    }
    
    if (whereClauses.length > 0) {
      const where = ' WHERE ' + whereClauses.join(' AND ');
      query += where;
      countQuery += where;
    }
    
    // 添加排序和分页
    query += ' ORDER BY f.feedback_time DESC LIMIT ? OFFSET ?';
    params.push(limitNum, offset);

    console.log('SQL:', query);
    console.log('Params:', params);

    // 执行查询
    const [feedbacks] = await db.query(query, params);

    // 获取总数
    const [countResult] = await db.query(countQuery, countParams);
    const total = countResult[0].total;

    res.json({
      success: true,
      data: feedbacks,
      pagination: {
        page: pageNum,
        limit: limitNum,
        total,
        pages: Math.ceil(total / limitNum)
      }
    });
    
  } catch (error) {
    console.error('获取反馈列表错误:', error);
    await writeSystemLog('error', `[FEEDBACK] 获取反馈列表失败: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器内部错误'
    });
  }
});

// 管理员根据反馈ID查看反馈详情 - 需要认证和管理员权限
router.get('/:id', authenticateToken, checkAdmin, async (req, res) => {
  try {
    const feedbackId = parseInt(req.params.id);
    
    const query = `
      SELECT f.*, u.username 
      FROM feedback f 
      JOIN users u ON f.user_id = u.user_id 
      WHERE f.feedback_id = ?
    `;
    
    const [rows] = await db.execute(query, [feedbackId]);
    
    if (rows.length === 0) {
      return res.status(404).json({
        success: false,
        message: '反馈不存在'
      });
    }
    
    res.json({
      success: true,
      data: rows[0],
      message: '反馈获取成功'
    });
  } catch (error) {
    console.error('获取反馈错误:', error);
    await writeSystemLog('error', `[FEEDBACK] 获取反馈失败: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器错误: ' + error.message
    });
  }
});

// 管理员回复反馈 - 需要认证和管理员权限
router.put('/:id/reply', authenticateToken, checkAdmin, async (req, res) => {
  try {
    const feedbackId = parseInt(req.params.id);
    const { answer } = req.body;
    
    // 验证参数
    if (!answer || answer.trim() === '') {
      await writeSystemLog('warning', `[FEEDBACK] 回复校验失败-内容为空 (反馈 ${feedbackId}, 管理员 ${req.user?.user_id})`);
      return res.status(400).json({
        success: false,
        message: '回复内容不能为空'
      });
    }
    
    if (answer.length > 500) {
      await writeSystemLog('warning', `[FEEDBACK] 回复校验失败-内容超过500字 (反馈 ${feedbackId}, 管理员 ${req.user?.user_id})`);
      return res.status(400).json({
        success: false,
        message: '回复内容不能超过500字'
      });
    }
    
    // 先检查反馈是否存在
    const checkQuery = `SELECT feedback_id FROM feedback WHERE feedback_id = ?`;
    const [checkRows] = await db.execute(checkQuery, [feedbackId]);
    
    if (checkRows.length === 0) {
      return res.status(404).json({
        success: false,
        message: '反馈不存在'
      });
    }
    
    // 更新反馈回复
    const updateQuery = `
      UPDATE feedback 
      SET answer = ?, answer_time = CURRENT_TIMESTAMP 
      WHERE feedback_id = ?
    `;
    
    await db.execute(updateQuery, [answer.trim(), feedbackId]);
    
    // 获取更新后的反馈信息
    const getQuery = `
      SELECT f.*, u.username 
      FROM feedback f 
      JOIN users u ON f.user_id = u.user_id 
      WHERE f.feedback_id = ?
    `;
    
    const [updatedRows] = await db.execute(getQuery, [feedbackId]);
    const fb = updatedRows[0];

    // 回复成功后，写入一条定向给反馈所有者的反馈通知（与系统通知统一走 notice + notice_read）
    // feedback_id 唯一索引保证同一反馈仅对应一条通知，重复回复时自然忽略
    const typeTitleMap = {
      suggestion: '建议反馈',
      ui: '界面反馈',
      bug: 'BUG反馈',
      other: '其他反馈'
    };
    try {
      await db.execute(
        `INSERT INTO notice (title, content, admin_id, target_user_id, feedback_id, feedback_time, feedback_type, reply, created_time)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())`,
        [
          typeTitleMap[fb.type] || '其他反馈',
          fb.content,
          req.user.user_id,
          fb.user_id,
          fb.feedback_id,
          fb.feedback_time,
          fb.type,
          answer.trim()
        ]
      );
    } catch (noticeErr) {
      // 通知写入失败不影响回复本身（feedback.answer 已更新）
      console.error('写入反馈通知失败:', noticeErr);
    }
    
    res.json({
      success: true,
      data: fb,
      message: '回复成功'
    });
    
  } catch (error) {
    console.error('回复反馈错误:', error);
    await writeSystemLog('error', `[FEEDBACK] 回复反馈失败: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器错误: ' + error.message
    });
  }
});

// 管理员根据ID删除反馈 - 需要认证和管理员权限
router.delete('/:id', authenticateToken, checkAdmin, async (req, res) => {
  try {
    const feedbackId = parseInt(req.params.id);
    
    // 先检查反馈是否存在
    const checkQuery = `SELECT feedback_id FROM feedback WHERE feedback_id = ?`;
    const [checkRows] = await db.execute(checkQuery, [feedbackId]);
    
    if (checkRows.length === 0) {
      return res.status(404).json({
        success: false,
        message: '反馈不存在'
      });
    }
    
    // 删除反馈
    const deleteQuery = `DELETE FROM feedback WHERE feedback_id = ?`;
    await db.execute(deleteQuery, [feedbackId]);
    
    res.json({
      success: true,
      message: '反馈删除成功'
    });
  } catch (error) {
    console.error('删除反馈错误:', error);
    await writeSystemLog('error', `[FEEDBACK] 删除反馈失败: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器错误: ' + error.message
    });
  }
});

module.exports = router;