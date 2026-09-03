// routes/history.js
const express = require('express');
const router = express.Router();
const dbModule = require('../db');
const db = dbModule.promisePool;
const { authenticateToken } = require('../middleware/auth');
const fs = require('fs');
const path = require('path');
const { writeSystemLog } = require('../utils/systemLog');



// 从数据库获取历史记录（修改：添加整型ID处理）
const getHistoryFromDatabase = async (userId, page = 1, limit = 20, search = '') => {
  try {
    // 确保userId是整型
    const userIdInt = parseInt(userId);
    if (isNaN(userIdInt)) {
      throw new Error('无效的用户ID');
    }
    
    // 构建查询条件
    let conditions = ['h.user_id = ?'];
    const params = [userIdInt];
    
    // 搜索条件
    if (search) {
      conditions.push('(h.generation_description LIKE ? OR d.data_name LIKE ?)');
      const searchTerm = `%${search}%`;
      params.push(searchTerm, searchTerm);
    }
    
    
    const whereClause = conditions.length > 0 ? 'WHERE ' + conditions.join(' AND ') : '';
    
    // 计算总数
    const countQuery = `
      SELECT COUNT(*) as total 
      FROM generation_history h
      LEFT JOIN data_file d ON h.data_id = d.data_id
      ${whereClause}
    `;
    const [countResult] = await db.query(countQuery, params);
    const total = countResult[0].total;
    
    // 计算分页
    const pageInt = parseInt(page);
    const limitInt = parseInt(limit);
    const offset = (pageInt - 1) * limitInt;
    
    // 获取数据
    const dataQuery = `
      SELECT 
        h.history_id as id,
        h.history_id,
        h.user_id,
        h.data_id,
        h.generation_description,
        h.generation_code,
        h.generation_path,
        h.generation_time,
        d.data_name,
        d.file_name
      FROM generation_history h
      LEFT JOIN data_file d ON h.data_id = d.data_id
      ${whereClause}
      ORDER BY h.history_id DESC
      LIMIT ? OFFSET ?
    `;
    
    const queryParams = [...params, limitInt, offset];
    const [records] = await db.query(dataQuery, queryParams);
    
    // 处理记录（确保ID为整型）
    const processedRecords = records.map(record => {
      return {
        id: parseInt(record.history_id),
        history_id: parseInt(record.history_id),
        user_id: parseInt(record.user_id),
        data_id: record.data_id ? parseInt(record.data_id) : null,
        description: record.generation_description,
        chart_code: record.generation_code,
        user_input: record.generation_description,
        ai_response: record.generation_code,
        file_name: record.data_name || '',
        generation_path: record.generation_path,
        created_at: record.generation_time,
        updated_at: record.generation_time
      };
    });
    
    return {
      records: processedRecords,
      total,
      page: pageInt,
      limit: limitInt
    };
    
  } catch (error) {
    console.error('从数据库获取历史记录失败:', error);
    await writeSystemLog('error', `[HISTORY] 获取历史记录列表失败: ${error.message}`);
    return { records: [], total: 0 };
  }
};

// 获取历史记录列表（修改：添加整型参数处理）
router.get('/', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;
    const {
      page = 1,
      limit = 20,
      search = '',
      start_date = '',
      end_date = ''
    } = req.query;

    // 确保参数为整型
    const pageInt = parseInt(page);
    const limitInt = parseInt(limit);
    
    if (isNaN(pageInt) || isNaN(limitInt) || pageInt < 1 || limitInt < 1) {
      return res.status(400).json({
        success: false,
        message: '分页参数无效'
      });
    }

    // 主要从数据库获取历史记录
    const historyResult = await getHistoryFromDatabase(userId, pageInt, limitInt, search);
    
    // 日期过滤
    if (start_date && end_date) {
      const start = new Date(start_date);
      const end = new Date(end_date);
      end.setHours(23, 59, 59, 999);
      
      historyResult.records = historyResult.records.filter(record => {
        const recordDate = new Date(record.created_at);
        return recordDate >= start && recordDate <= end;
      });
      
      historyResult.total = historyResult.records.length;
    }
    
    res.json({
      success: true,
      data: {
        records: historyResult.records,
        total: historyResult.total,
        page: pageInt,
        limit: limitInt,
        total_pages: Math.ceil(historyResult.total / limitInt)
      }
    });

  } catch (error) {
    console.error('获取历史记录错误:', error);
    await writeSystemLog('error', `[HISTORY] 获取历史记录失败: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器内部错误'
    });
  }
});

// 获取单个历史记录详情（修改：添加整型ID处理）
router.get('/:id', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;
    const recordId = parseInt(req.params.id);
    
    // 验证ID是否为有效整型
    if (isNaN(recordId)) {
      return res.status(400).json({
        success: false,
        message: '无效的历史记录ID'
      });
    }
    
    // 从数据库查找
    const query = `
      SELECT 
        h.history_id,
        h.user_id,
        h.data_id,
        h.generation_description,
        h.generation_code,
        h.generation_path,
        h.generation_time,
        d.data_name,
        d.file_name
      FROM generation_history h
      LEFT JOIN data_file d ON h.data_id = d.data_id
      WHERE h.history_id = ? AND h.user_id = ?
      LIMIT 1
    `;
    
    const [records] = await db.query(query, [recordId, parseInt(userId)]);
    
    if (records.length === 0) {
      return res.status(404).json({
        success: false,
        message: '历史记录不存在'
      });
    }
    
    const record = records[0];
    const processedRecord = {
      id: parseInt(record.history_id),
      history_id: parseInt(record.history_id),
      user_id: parseInt(record.user_id),
      data_id: record.data_id ? parseInt(record.data_id) : null,
      description: record.generation_description,
      chart_code: record.generation_code,
      user_input: record.generation_description,
      ai_response: record.generation_code,
      file_name: record.data_name || '',
      generation_path: record.generation_path,
      created_at: record.generation_time,
      updated_at: record.generation_time
    };

    res.json({
      success: true,
      data: processedRecord
    });

  } catch (error) {
    console.error('获取历史记录详情错误:', error);
    await writeSystemLog('error', `[HISTORY] 获取历史记录详情失败: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器内部错误'
    });
  }
});

// 删除历史记录（修改：添加整型ID处理）
router.delete('/:id', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;
    const recordId = parseInt(req.params.id);
    
    // 验证ID是否为有效整型
    if (isNaN(recordId)) {
      return res.status(400).json({
        success: false,
        message: '无效的历史记录ID'
      });
    }
    
    // 使用事务确保数据一致性
    const connection = await db.getConnection();
    await connection.beginTransaction();
    
    try {
      // 1. 先删除对应的API日志（子表）
      const deleteApiLogQuery = 'DELETE FROM api_log WHERE history_id = ? AND user_id = ?';
      await connection.query(deleteApiLogQuery, [recordId, parseInt(userId)]);
      
      // 2. 再删除历史记录（主表）
      const deleteQuery = 'DELETE FROM generation_history WHERE history_id = ? AND user_id = ?';
      const [result] = await connection.query(deleteQuery, [recordId, parseInt(userId)]);
      
      if (result.affectedRows === 0) {
        await connection.rollback();
        return res.status(404).json({
          success: false,
          message: '历史记录不存在'
        });
      }
      
      // 提交事务
      await connection.commit();
      
      console.log(`用户 ${userId} 删除了历史记录 ID: ${recordId}`);
      
      res.json({
        success: true,
        message: '历史记录已删除',
        data: { 
          deleted_id: recordId
        }
      });
      
    } catch (error) {
      // 回滚事务
      await connection.rollback();
      throw error;
    } finally {
      // 释放连接
      connection.release();
    }

  } catch (error) {
    console.error('删除历史记录错误:', error);
    await writeSystemLog('error', `[HISTORY] 删除历史记录失败: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器内部错误'
    });
  }
});

// 统计信息（修改：添加整型用户ID处理）
router.get('/stats/summary', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;
    const userIdInt = parseInt(userId);
    
    if (isNaN(userIdInt)) {
      return res.status(400).json({
        success: false,
        message: '无效的用户ID'
      });
    }

    // 从数据库获取统计
    let dbStats = { total: 0, by_type: {} };
    try {
      // 总数统计
      const countQuery = 'SELECT COUNT(*) as total FROM generation_history WHERE user_id = ?';
      const [countResult] = await db.query(countQuery, [userIdInt]);
      dbStats.total = countResult[0].total || 0;
    } catch (dbError) {
      console.error('获取数据库统计失败:', dbError);
    }
    
    // 最近7天统计
    let recentCount = 0;
    try {
      const sevenDaysAgo = new Date();
      sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
      
      const recentQuery = `
        SELECT COUNT(*) as count 
        FROM generation_history 
        WHERE user_id = ? AND generation_time >= ?
      `;
      const [recentResult] = await db.query(recentQuery, [userIdInt, sevenDaysAgo]);
      recentCount = parseInt(recentResult[0].count) || 0;
    } catch (error) {
      console.error('获取最近统计失败:', error);
      await writeSystemLog('error', `[HISTORY] 获取最近统计失败: ${error.message}`);
    }

    res.json({
      success: true,
      data: {
        total_count: dbStats.total,
        recent_count: recentCount,
        last_7_days: recentCount
      }
    });

  } catch (error) {
    console.error('获取统计信息错误:', error);
    await writeSystemLog('error', `[HISTORY] 获取统计信息失败: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器内部错误'
    });
  }
});

// 导出历史记录为CSV（修改：添加整型用户ID处理）
router.get('/export/csv', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.user_id;
    const userIdInt = parseInt(userId);
    
    if (isNaN(userIdInt)) {
      return res.status(400).json({
        success: false,
        message: '无效的用户ID'
      });
    }
    
    // 获取用户的历史记录
    const historyResult = await getHistoryFromDatabase(userIdInt, 1, 1000);
    
    const headers = ['ID',  '描述', '文件名', '生成时间', '代码长度'];
    const csvRows = historyResult.records.map(record => [
      record.id,
      `"${(record.description || '').replace(/"/g, '""')}"`,
      record.file_name || '',
      record.created_at,
      record.chart_code ? record.chart_code.length : 0
    ]);

    const csvContent = [
      headers.join(','),
      ...csvRows.map(row => row.join(','))
    ].join('\n');

    // 设置响应头
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="chart_history_${userId}_${Date.now()}.csv"`);

    res.send(csvContent);

  } catch (error) {
    console.error('导出历史记录错误:', error);
    await writeSystemLog('error', `[HISTORY] 导出历史记录失败: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器内部错误'
    });
  }
});

module.exports = router;