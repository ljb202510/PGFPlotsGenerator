// backend/routes/AdminNotice.js
const express = require('express');
const router = express.Router();
const dbModule = require('../db');
const db = dbModule.promisePool;

// 获取通知列表（管理员）
router.get('/', async (req, res) => {
  try {
    const {
      page = 1,
      pageSize = 10,
      keyword = '',
      startDate = '',
      endDate = ''
    } = req.query;

    const offset = (page - 1) * pageSize;
    
    // 构建查询条件：管理端只管理系统广播通知，排除方案B写入的反馈回复通知
    let whereConditions = ['n.feedback_id IS NULL'];
    let queryParams = [];

    if (keyword) {
      whereConditions.push('(n.title LIKE ? OR n.content LIKE ?)');
      queryParams.push(`%${keyword}%`, `%${keyword}%`);
    }
    
    if (startDate) {
      whereConditions.push('n.created_time >= ?');
      queryParams.push(startDate + ' 00:00:00');
    }
    
    if (endDate) {
      whereConditions.push('n.created_time <= ?');
      queryParams.push(endDate + ' 23:59:59');
    }

    const whereClause = whereConditions.length > 0 
      ? `WHERE ${whereConditions.join(' AND ')}` 
      : '';

    // 获取总数
    const [countResult] = await db.query(
      `SELECT COUNT(*) as total FROM notice n ${whereClause}`,
      queryParams
    );
    const total = countResult[0].total;

    // 获取分页数据（附带每条通知的已读人数）
    const [notices] = await db.query(
      `SELECT n.*, u.username as admin_name,
              (SELECT COUNT(*) FROM notice_read nr WHERE nr.notice_id = n.notice_id) as read_count
       FROM notice n 
       LEFT JOIN users u ON n.admin_id = u.user_id 
       ${whereClause}
       ORDER BY n.created_time DESC 
       LIMIT ? OFFSET ?`,
      [...queryParams, parseInt(pageSize), offset]
    );

    // 获取统计数据（基于 notice_read 表，按"至少被一位用户读过"的条数计算）
    const [readCountResult] = await db.query(
      `SELECT COUNT(DISTINCT nr.notice_id) as count 
       FROM notice_read nr 
       JOIN notice n ON nr.notice_id = n.notice_id AND n.feedback_id IS NULL`
    );
    const [unreadCountResult] = await db.query(
      `SELECT COUNT(*) as count FROM notice 
       WHERE feedback_id IS NULL AND notice_id NOT IN (SELECT DISTINCT notice_id FROM notice_read)`
    );

    const readCount = readCountResult[0].count;
    const unreadCount = unreadCountResult[0].count;

    res.json({
      code: 200,
      message: '获取通知列表成功',
      data: {
        notices: notices,
        pagination: {
          page: parseInt(page),
          pageSize: parseInt(pageSize),
          total,
          totalPages: Math.ceil(total / pageSize)
        },
        statistics: {
          total_count: total,
          read_count: readCount,
          unread_count: unreadCount
        }
      }
    });
  } catch (error) {
    console.error('获取通知列表时出错:', error);
    res.status(500).json({
      code: 500,
      message: '服务器内部错误',
      data: null
    });
  }
});

// 获取通知详情
router.get('/:id', async (req, res) => {
  try {
    const { id } = req.params;
    
    const [notices] = await db.query(
      `SELECT n.*, u.username as admin_name 
       FROM notice n 
       LEFT JOIN users u ON n.admin_id = u.user_id 
       WHERE n.notice_id = ? AND n.feedback_id IS NULL`,
      [id]
    );

    if (notices.length === 0) {
      return res.status(404).json({
        code: 404,
        message: '通知不存在',
        data: null
      });
    }

    const notice = notices[0];

    res.json({
      code: 200,
      message: '获取通知详情成功',
      data: notice
    });
  } catch (error) {
    console.error('获取通知详情时出错:', error);
    res.status(500).json({
      code: 500,
      message: '服务器内部错误',
      data: null
    });
  }
});

// 创建通知
router.post('/', async (req, res) => {
  try {
    const { title, content } = req.body;
    const adminId = 1; // 模拟管理员ID，实际应用中应该从认证信息中获取

    if (!title || !content) {
      return res.status(400).json({
        code: 400,
        message: '标题和内容不能为空',
        data: null
      });
    }

    // 插入新通知
    const [insertResult] = await db.query(
      'INSERT INTO notice (title, content, admin_id) VALUES (?, ?, ?)',
      [title, content, adminId]
    );

    const noticeId = insertResult.insertId;

    res.json({
      code: 200,
      message: '通知创建成功',
      data: {
        notice_id: noticeId
      }
    });
  } catch (error) {
    console.error('创建通知时出错:', error);
    res.status(500).json({
      code: 500,
      message: '服务器内部错误',
      data: null
    });
  }
});

// 更新通知
router.put('/:id', async (req, res) => {
  try {
    const { id } = req.params;
    const { title, content } = req.body;

    if (!title || !content) {
      return res.status(400).json({
        code: 400,
        message: '标题和内容不能为空',
        data: null
      });
    }

    // 检查通知是否存在（仅系统通知可编辑）
    const [existingNotices] = await db.query(
      'SELECT notice_id FROM notice WHERE notice_id = ? AND feedback_id IS NULL',
      [id]
    );

    if (existingNotices.length === 0) {
      return res.status(404).json({
        code: 404,
        message: '通知不存在',
        data: null
      });
    }

    // 更新通知
    await db.query(
      'UPDATE notice SET title = ?, content = ? WHERE notice_id = ? AND feedback_id IS NULL',
      [title, content, id]
    );

    res.json({
      code: 200,
      message: '通知更新成功',
      data: null
    });
  } catch (error) {
    console.error('更新通知时出错:', error);
    res.status(500).json({
      code: 500,
      message: '服务器内部错误',
      data: null
    });
  }
});

// 删除通知
router.delete('/:id', async (req, res) => {
  try {
    const { id } = req.params;

    // 检查通知是否存在（仅系统通知可删除）
    const [existingNotices] = await db.query(
      'SELECT notice_id FROM notice WHERE notice_id = ? AND feedback_id IS NULL',
      [id]
    );

    if (existingNotices.length === 0) {
      return res.status(404).json({
        code: 404,
        message: '通知不存在',
        data: null
      });
    }

    // 删除通知
    await db.query('DELETE FROM notice WHERE notice_id = ? AND feedback_id IS NULL', [id]);

    res.json({
      code: 200,
      message: '通知删除成功',
      data: null
    });
  } catch (error) {
    console.error('删除通知时出错:', error);
    res.status(500).json({
      code: 500,
      message: '服务器内部错误',
      data: null
    });
  }
});

// 批量删除通知
router.delete('/', async (req, res) => {
  try {
    const { ids } = req.body;

    if (!ids || !Array.isArray(ids) || ids.length === 0) {
      return res.status(400).json({
        code: 400,
        message: '请选择要删除的通知',
        data: null
      });
    }

    // 批量删除通知（仅系统通知，避免误删反馈回复通知）
    await db.query('DELETE FROM notice WHERE notice_id IN (?) AND feedback_id IS NULL', [ids]);

    res.json({
      code: 200,
      message: '批量删除成功',
      data: null
    });
  } catch (error) {
    console.error('批量删除通知时出错:', error);
    res.status(500).json({
      code: 500,
      message: '服务器内部错误',
      data: null
    });
  }
});

// 获取通知统计数据
router.get('/statistics/overview', async (req, res) => {
  try {
    // 获取最近30天的阅读统计（按阅读时间统计每日已读次数）
    const [recentStats] = await db.query(`
      SELECT 
        DATE(nr.read_time) as date,
        COUNT(*) as count,
        COUNT(*) as read_count
      FROM notice_read nr
      WHERE nr.read_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
        AND nr.notice_id IN (SELECT notice_id FROM notice WHERE feedback_id IS NULL)
      GROUP BY DATE(nr.read_time)
      ORDER BY date DESC
    `);

    // 获取管理员发布统计（已读人数取该管理员发布通知的已读记录数）
    const [adminStats] = await db.query(`
      SELECT 
        u.user_id,
        u.username,
        COUNT(n.notice_id) as notice_count,
        (SELECT COUNT(*) FROM notice_read nr WHERE nr.notice_id IN (
          SELECT n2.notice_id FROM notice n2 WHERE n2.admin_id = u.user_id AND n2.feedback_id IS NULL
        )) as read_count
      FROM users u
      LEFT JOIN notice n ON u.user_id = n.admin_id AND n.feedback_id IS NULL
      WHERE u.role = 'admin'
      GROUP BY u.user_id, u.username
    `);

    // 获取阅读率统计（至少被一位用户读过的通知条数）
    const [summaryResult] = await db.query(`
      SELECT 
        COUNT(*) as total,
        (SELECT COUNT(DISTINCT nr.notice_id) FROM notice_read nr JOIN notice n ON nr.notice_id = n.notice_id AND n.feedback_id IS NULL) as read_count
      FROM notice
      WHERE feedback_id IS NULL
    `);

    const total = summaryResult[0].total;
    const readCount = summaryResult[0].read_count || 0;
    const readRatePercent = total > 0 ? ((readCount / total) * 100).toFixed(2) : 0;

    res.json({
      code: 200,
      message: '获取统计数据成功',
      data: {
        recent: recentStats,
        adminStats,
        summary: {
          total,
          readCount,
          unreadCount: total - readCount,
          readRate: parseFloat(readRatePercent)
        }
      }
    });
  } catch (error) {
    console.error('获取统计数据时出错:', error);
    res.status(500).json({
      code: 500,
      message: '服务器内部错误',
      data: null
    });
  }
});

module.exports = router;