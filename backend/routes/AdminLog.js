// AdminLog.js - 使用MySQL数据库的后端接口
const express = require('express');
const router = express.Router();

// 导入数据库连接池
const dbModule = require('../db');
const db = dbModule.promisePool;

// 工具函数：格式化数据库查询结果
const formatQueryResult = (results) => {
  return JSON.parse(JSON.stringify(results));
};

// 工具函数：从日期时间字符串中提取日期部分
const extractDateFromDateTime = (dateTimeStr) => {
  if (!dateTimeStr) return '';
  
  // 处理多种可能的日期时间格式
  if (dateTimeStr.includes('T')) {
    // ISO格式: "2024-01-20T10:30:00.000Z"
    return dateTimeStr.split('T')[0];
  } else if (dateTimeStr.includes(' ')) {
    // MySQL DATETIME格式: "2024-01-20 10:30:00"
    return dateTimeStr.split(' ')[0];
  } else {
    // 纯日期格式: "2024-01-20"
    return dateTimeStr.substring(0, 10);
  }
};

// 获取API调用统计信息
router.get('/api-stats', async (req, res) => {
  let connection;
  try {
    const { startDate, endDate } = req.query;
    
    // 构建基础查询
    let baseQuery = `
      SELECT al.call_id, al.user_id, al.history_id, al.call_status, 
             al.call_time, al.call_error, u.username, gh.generation_description
      FROM api_log al
      LEFT JOIN users u ON al.user_id = u.user_id
      LEFT JOIN generation_history gh ON al.history_id = gh.history_id
    `;
    
    const whereConditions = [];
    const queryParams = [];
    
    // 添加日期范围条件
    if (startDate && endDate) {
      whereConditions.push('al.call_time BETWEEN ? AND ?');
      queryParams.push(startDate, endDate + ' 23:59:59');
    }
    
    if (whereConditions.length > 0) {
      baseQuery += ` WHERE ${whereConditions.join(' AND ')}`;
    }
    
    baseQuery += ' ORDER BY al.call_time DESC';
    
    connection = await db.getConnection();
    
    // 获取API日志数据
    const [apiLogs] = await connection.execute(baseQuery, queryParams);
    const formattedApiLogs = formatQueryResult(apiLogs);
    
    // 计算统计信息
    const totalCalls = formattedApiLogs.length;
    const successCalls = formattedApiLogs.filter(log => log.call_status === 'success').length;
    const failedCalls = formattedApiLogs.filter(log => log.call_status === 'failed').length;
    const successRate = totalCalls > 0 ? ((successCalls / totalCalls) * 100).toFixed(2) : 0;
    
    // 按日期分组统计（用于图表）
    const timeSeriesData = {};
    formattedApiLogs.forEach(log => {
      const date = extractDateFromDateTime(log.call_time);
      if (!date) return; // 跳过无效日期
      
      if (!timeSeriesData[date]) {
        timeSeriesData[date] = { date, total: 0, success: 0, failed: 0 };
      }
      timeSeriesData[date].total++;
      if (log.call_status === 'success') {
        timeSeriesData[date].success++;
      } else {
        timeSeriesData[date].failed++;
      }
    });
    
    // 转换为数组并按日期排序
    const timeSeries = Object.values(timeSeriesData)
      .sort((a, b) => new Date(a.date) - new Date(b.date));
    
    // 获取最近失败的调用详情
    const recentFailures = formattedApiLogs
      .filter(log => log.call_status === 'failed')
      .slice(0, 10) // 由于已经按时间倒序排序，直接取前10个
      .map(log => ({
        call_id: log.call_id,
        user_id: log.user_id,
        username: log.username || 'Unknown',
        call_status: log.call_status,
        call_time: log.call_time,
        call_error: log.call_error,
        generation_description: log.generation_description || 'No description'
      }));
    
    // 计算响应时间（模拟数据，实际应从API响应中获取）
    const avgResponseTime = 245; // 模拟平均响应时间(ms)
    const p95ResponseTime = 420; // 模拟P95响应时间(ms)
    
    res.json({
      success: true,
      data: {
        summary: {
          total_calls: totalCalls,
          success_calls: successCalls,
          failed_calls: failedCalls,
          success_rate: parseFloat(successRate)
        },
        timeSeries,
        recentFailures,
        responseTime: {
          avg: avgResponseTime,
          p95: p95ResponseTime
        }
      }
    });
  } catch (error) {
    console.error('获取API统计失败:', error);
    res.status(500).json({ 
      success: false, 
      message: '获取数据失败',
      error: error.message 
    });
  } finally {
    if (connection) connection.release();
  }
});

// 获取系统日志
router.get('/system-logs', async (req, res) => {
  let connection;
  try {
    const { 
      page = 1, 
      pageSize = 20, 
      status, 
      startDate, 
      endDate,
      search 
    } = req.query;
    
    // 确保参数是数字类型
    const pageInt = parseInt(page);
    const pageSizeInt = parseInt(pageSize);
    const offset = (pageInt - 1) * pageSizeInt;
    
    console.log('查询参数:', { page: pageInt, pageSize: pageSizeInt, offset, status, search });
    
    // 构建基础查询 - 注意：LIMIT和OFFSET不使用占位符，直接拼接到SQL中
    let baseQuery = `
      SELECT sys_id, system_status, log_time, error
      FROM system_log
    `;
    
    let countQuery = `SELECT COUNT(*) as total FROM system_log`;
    
    const whereConditions = [];
    const queryParams = [];
    const countParams = [];
    
    // 添加过滤条件
    if (status && status !== 'all') {
      whereConditions.push('system_status = ?');
      queryParams.push(status);
      countParams.push(status);
    }
    
    if (search) {
      whereConditions.push('error LIKE ?');
      queryParams.push(`%${search}%`);
      countParams.push(`%${search}%`);
    }
    
    if (startDate && endDate) {
      whereConditions.push('log_time BETWEEN ? AND ?');
      queryParams.push(startDate, endDate + ' 23:59:59');
      countParams.push(startDate, endDate + ' 23:59:59');
    }
    
    // 添加WHERE条件
    if (whereConditions.length > 0) {
      const whereClause = ` WHERE ${whereConditions.join(' AND ')}`;
      baseQuery += whereClause;
      countQuery += whereClause;
    }
    
    // 修复：将分页参数直接拼接到SQL字符串中，而不是使用占位符
    baseQuery += ` ORDER BY log_time DESC LIMIT ${pageSizeInt} OFFSET ${offset}`;
    
    console.log('执行查询:', baseQuery);
    console.log('查询参数:', queryParams);
    
    connection = await db.getConnection();
    
    // 获取日志数据
    const [logs] = await connection.query(baseQuery, queryParams);
    const [countResult] = await connection.query(countQuery, countParams);
    
    const total = countResult[0] ? countResult[0].total : 0;
    const formattedLogs = formatQueryResult(logs);
    
    // 添加状态类型字段
    const logsWithType = formattedLogs.map(log => ({
      ...log,
      status_type: 
        log.system_status === 'error' ? 'danger' :
        log.system_status === 'warning' ? 'warning' : 'success'
    }));
    
    // 获取日志统计（最近7天）
    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
    const sevenDaysAgoStr = sevenDaysAgo.toISOString().split('T')[0];
    
    const [statsResult] = await connection.execute(
      `SELECT system_status, COUNT(*) as count 
       FROM system_log 
       WHERE log_time >= ? 
       GROUP BY system_status`,
      [sevenDaysAgoStr]
    );
    
    const statsArray = formatQueryResult(statsResult);
    
    res.json({
      success: true,
      data: {
        logs: logsWithType,
        pagination: {
          page: pageInt,
          pageSize: pageSizeInt,
          total,
          totalPages: Math.ceil(total / pageSizeInt)
        },
        stats: statsArray
      }
    });
  } catch (error) {
    console.error('获取系统日志失败:', error);
    console.error('错误详情:', {
      message: error.message,
      code: error.code,
      sql: error.sql
    });
    res.status(500).json({ 
      success: false, 
      message: '获取系统日志失败',
      error: error.message 
    });
  } finally {
    if (connection) connection.release();
  }
});

// 获取系统健康状况概览
router.get('/health-overview', async (req, res) => {
  let connection;
  try {
    connection = await db.getConnection();
    
    // 计算最近1小时的API调用情况
    const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
    
    const [recentApiResult] = await connection.execute(
      `SELECT call_status, COUNT(*) as count 
       FROM api_log 
       WHERE call_time >= ? 
       GROUP BY call_status`,
      [oneHourAgo]
    );
    
    const recentApiStats = formatQueryResult(recentApiResult);
    
    // 获取最近1小时的错误日志数
    const [recentErrorResult] = await connection.execute(
      `SELECT COUNT(*) as count 
       FROM system_log 
       WHERE system_status = 'error' AND log_time >= ?`,
      [oneHourAgo]
    );
    
    const recentErrors = recentErrorResult[0] ? recentErrorResult[0].count : 0;
    
    // 获取最近24小时的活跃用户数
    const twentyFourHoursAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
    
    const [activeUsersResult] = await connection.execute(
      `SELECT COUNT(DISTINCT user_id) as count 
       FROM (
         SELECT user_id FROM api_log WHERE call_time >= ?
         UNION 
         SELECT user_id FROM generation_history WHERE generation_time >= ?
       ) as active_users`,
      [twentyFourHoursAgo, twentyFourHoursAgo]
    );
    
    const activeUsers = activeUsersResult[0] ? activeUsersResult[0].count : 0;
    
    // 获取最近24小时的数据文件统计
    const [filesResult] = await connection.execute(
      `SELECT COUNT(*) as file_count, COALESCE(SUM(data_size), 0) as total_size 
       FROM data_file 
       WHERE load_time >= ?`,
      [twentyFourHoursAgo]
    );
    
    const fileStats = formatQueryResult(filesResult)[0] || { file_count: 0, total_size: 0 };
    const totalFiles = fileStats.file_count;
    const totalSizeMB = (fileStats.total_size / 1048576).toFixed(2);
    
    // 计算系统健康状态
    let systemStatus = 'healthy';
    let statusColor = 'success';
    
    if (recentErrors > 10) {
      systemStatus = 'critical';
      statusColor = 'danger';
    } else if (recentErrors > 3) {
      systemStatus = 'warning';
      statusColor = 'warning';
    }
    
    res.json({
      success: true,
      data: {
        systemStatus,
        statusColor,
        metrics: {
          recentApi: recentApiStats,
          recentErrors,
          activeUsers,
          newFiles: totalFiles,
          storageUsed: parseFloat(totalSizeMB)
        }
      }
    });
  } catch (error) {
    console.error('获取系统健康概览失败:', error);
    res.status(500).json({ 
      success: false, 
      message: '获取系统健康概览失败',
      error: error.message 
    });
  } finally {
    if (connection) connection.release();
  }
});

// 添加系统日志（用于测试）
router.post('/add-test-log', async (req, res) => {
  let connection;
  try {
    const { message = '测试日志', status = 'normal' } = req.body;
    
    connection = await db.getConnection();
    
    const [result] = await connection.execute(
      `INSERT INTO system_log (system_status, log_time, error) 
       VALUES (?, NOW(), ?)`,
      [status, message]
    );
    
    res.json({
      success: true,
      message: '测试日志添加成功',
      log_id: result.insertId
    });
  } catch (error) {
    console.error('添加测试日志失败:', error);
    res.status(500).json({ 
      success: false, 
      message: '添加测试日志失败',
      error: error.message 
    });
  } finally {
    if (connection) connection.release();
  }
});

// 获取数据库表状态
router.get('/tables-status', async (req, res) => {
  let connection;
  try {
    connection = await db.getConnection();
    
    // 获取各表记录数
    const tables = ['users', 'api_log', 'system_log', 'generation_history', 'data_file'];
    const status = {};
    
    for (const table of tables) {
      const [result] = await connection.execute(`SELECT COUNT(*) as count FROM ${table}`);
      status[table] = result[0] ? result[0].count : 0;
    }
    
    res.json({
      success: true,
      data: status
    });
  } catch (error) {
    console.error('获取表状态失败:', error);
    res.status(500).json({ 
      success: false, 
      message: '获取表状态失败',
      error: error.message 
    });
  } finally {
    if (connection) connection.release();
  }
});

module.exports = router;