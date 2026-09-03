// backend/routes/notice.js
const express = require('express');
const router = express.Router();
const dbModule = require('../db');
const db = dbModule.promisePool;
const { authenticateToken } = require('../middleware/auth');

// 获取用户通知列表（系统通知 + 反馈回复，统一走 notice + notice_read 一套机制）
// 反馈回复在管理员回复时即写入 notice 表（target_user_id=反馈所有者，feedback_id 关联来源）
router.get('/', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;

    const [rows] = await db.execute(
      `SELECT n.notice_id,
              n.title,
              n.content,
              n.created_time,
              n.feedback_id,
              n.feedback_time,
              n.feedback_type,
              n.reply,
              CASE WHEN nr.id IS NOT NULL THEN 1 ELSE 0 END AS is_read
       FROM notice n
       LEFT JOIN notice_read nr ON n.notice_id = nr.notice_id AND nr.user_id = ?
       WHERE n.target_user_id IS NULL OR n.target_user_id = ?
       ORDER BY n.created_time DESC`,
      [userId, userId]
    );

    const notices = rows.map(r => {
      const time = r.feedback_id !== null
        ? (r.feedback_time || r.created_time)
        : r.created_time;
      return {
        id: r.notice_id,
        type: r.feedback_id !== null ? 'feedback' : 'system',
        title: r.title,
        content: r.content,
        time,
        isRead: !!r.is_read,
        feedbackTime: r.feedback_id !== null ? (r.feedback_time || r.created_time) : null,
        feedbackType: r.feedback_type,
        reply: r.reply
      };
    });

    const unreadCount = notices.filter(n => !n.isRead).length;

    res.json({
      code: 200,
      message: '获取通知成功',
      data: {
        notices,
        unreadCount,
        totalCount: notices.length
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

// 标记单条通知为已读（统一写 notice_read）
router.post('/read/:id', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;
    const noticeId = parseInt(req.params.id, 10);

    if (Number.isNaN(noticeId)) {
      return res.status(400).json({ code: 400, message: '无效的通知 ID', data: null });
    }

    await db.execute(
      'INSERT IGNORE INTO notice_read (user_id, notice_id, read_time) VALUES (?, ?, NOW())',
      [userId, noticeId]
    );

    res.json({ code: 200, message: '标记已读成功', data: null });
  } catch (error) {
    console.error('标记已读时出错:', error);
    res.status(500).json({ code: 500, message: '服务器内部错误', data: null });
  }
});

// 标记所有可见通知为已读
router.post('/read-all', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;

    await db.execute(
      `INSERT IGNORE INTO notice_read (user_id, notice_id, read_time)
       SELECT ?, notice_id, NOW()
       FROM notice
       WHERE target_user_id IS NULL OR target_user_id = ?`,
      [userId, userId]
    );

    res.json({ code: 200, message: '全部标记已读成功', data: null });
  } catch (error) {
    console.error('全部标记已读时出错:', error);
    res.status(500).json({ code: 500, message: '服务器内部错误', data: null });
  }
});

// 获取未读通知数量（按用户可见范围统一统计）
router.get('/unread-count', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;

    const [rows] = await db.execute(
      `SELECT COUNT(*) AS total,
              SUM(CASE WHEN nr.id IS NULL THEN 1 ELSE 0 END) AS unread
       FROM notice n
       LEFT JOIN notice_read nr ON n.notice_id = nr.notice_id AND nr.user_id = ?
       WHERE n.target_user_id IS NULL OR n.target_user_id = ?`,
      [userId, userId]
    );

    const total = parseInt(rows[0].total) || 0;
    const totalUnread = parseInt(rows[0].unread) || 0;

    res.json({
      code: 200,
      message: '获取未读数量成功',
      data: {
        unreadCount: totalUnread,
        feedbackUnread: totalUnread, // 历史字段保留，供导航栏红点使用
        systemUnread: 0
      }
    });
  } catch (error) {
    console.error('获取未读数量时出错:', error);
    res.status(500).json({ code: 500, message: '服务器内部错误', data: null });
  }
});

module.exports = router;
