// utils/systemLog.js - 共享系统日志写入工具
// 写入 system_log 表（system_status ENUM('normal','warning','error')）。
// 仅作为副作用使用：自身失败只 console.error，绝不向上抛出，不影响主请求响应。
const dbModule = require('../db');
const db = dbModule.promisePool;

/**
 * 写入一条系统日志。
 * @param {'normal'|'warning'|'error'} status 日志级别
 * @param {string} message 日志内容（自动截断为 500 字）
 */
async function writeSystemLog(status, message) {
  try {
    await db.query(
      `INSERT INTO system_log (system_status, log_time, error)
       VALUES (?, NOW(), ?)`,
      [status, String(message || '').substring(0, 500)]
    );
  } catch (e) {
    console.error('system_log 写入失败:', e.message);
  }
}

module.exports = { writeSystemLog };
