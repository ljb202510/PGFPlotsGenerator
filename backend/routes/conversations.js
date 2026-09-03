// routes/conversations.js
// 对话（会话）持久化接口：列表 / 新建 / 重命名 / 删除 / 获取消息
const express = require('express');
const router = express.Router();
const dbModule = require('../db');
const db = dbModule.promisePool;
const { authenticateToken } = require('../middleware/auth');

// 列表（含最后一条消息预览与消息数，按最近更新倒序）
router.get('/', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;
    const [rows] = await db.query(`
      SELECT
        c.conversation_id,
        c.title,
        c.created_at,
        c.updated_at,
        (SELECT COUNT(*) FROM conversation_messages m WHERE m.conversation_id = c.conversation_id) AS message_count,
        (SELECT content FROM conversation_messages m WHERE m.conversation_id = c.conversation_id ORDER BY m.message_id DESC LIMIT 1) AS last_message
      FROM conversations c
      WHERE c.user_id = ?
      ORDER BY c.updated_at DESC, c.conversation_id DESC
    `, [userId]);

    res.json({
      success: true,
      data: rows.map(r => ({
        conversation_id: r.conversation_id,
        title: r.title,
        message_count: r.message_count,
        last_message: r.last_message || '',
        created_at: r.created_at,
        updated_at: r.updated_at
      }))
    });
  } catch (error) {
    console.error('获取对话列表失败:', error);
    res.status(500).json({ success: false, message: '服务器内部错误' });
  }
});

// 新建对话
router.post('/', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;
    const title = (req.body.title || '新对话').toString().trim().slice(0, 255) || '新对话';
    const [result] = await db.query(
      'INSERT INTO conversations (user_id, title) VALUES (?, ?)',
      [userId, title]
    );
    res.json({
      success: true,
      data: { conversation_id: result.insertId, title }
    });
  } catch (error) {
    console.error('新建对话失败:', error);
    res.status(500).json({ success: false, message: '服务器内部错误' });
  }
});

// 重命名
router.put('/:id', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;
    const id = parseInt(req.params.id);
    const title = (req.body.title || '').toString().trim();
    if (!Number.isInteger(id) || !title) {
      return res.status(400).json({ success: false, message: '缺少标题或ID无效' });
    }
    await db.query(
      'UPDATE conversations SET title = ? WHERE conversation_id = ? AND user_id = ?',
      [title.slice(0, 255), id, userId]
    );
    res.json({ success: true });
  } catch (error) {
    console.error('重命名对话失败:', error);
    res.status(500).json({ success: false, message: '服务器内部错误' });
  }
});

// 删除（连同消息）
router.delete('/:id', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;
    const id = parseInt(req.params.id);
    if (!Number.isInteger(id)) {
      return res.status(400).json({ success: false, message: 'ID无效' });
    }
    const conn = await db.getConnection();
    await conn.beginTransaction();
    try {
      await conn.query(
        'DELETE FROM conversation_messages WHERE conversation_id = ? AND user_id = ?',
        [id, userId]
      );
      const [result] = await conn.query(
        'DELETE FROM conversations WHERE conversation_id = ? AND user_id = ?',
        [id, userId]
      );
      await conn.commit();
      if (result.affectedRows === 0) {
        return res.status(404).json({ success: false, message: '对话不存在' });
      }
      res.json({ success: true });
    } catch (e) {
      await conn.rollback();
      throw e;
    } finally {
      conn.release();
    }
  } catch (error) {
    console.error('删除对话失败:', error);
    res.status(500).json({ success: false, message: '服务器内部错误' });
  }
});

// 获取某对话的消息（按时间正序）
router.get('/:id/messages', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;
    const id = parseInt(req.params.id);
    if (!Number.isInteger(id)) {
      return res.status(400).json({ success: false, message: 'ID无效' });
    }
    const [rows] = await db.query(`
      SELECT message_id, role, content, chart_code, history_id, selected_files, created_at
      FROM conversation_messages
      WHERE conversation_id = ? AND user_id = ?
      ORDER BY message_id ASC
    `, [id, userId]);

    res.json({
      success: true,
      data: rows.map(r => ({
        message_id: r.message_id,
        role: r.role,
        content: r.content,
        chart_code: r.chart_code || null,
        history_id: r.history_id || null,
        selected_files: r.selected_files || null,
        created_at: r.created_at
      }))
    });
  } catch (error) {
    console.error('获取对话消息失败:', error);
    res.status(500).json({ success: false, message: '服务器内部错误' });
  }
});

module.exports = router;
