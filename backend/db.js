// db.js - 共享连接池
const mysql = require('mysql2/promise');

// 凭据优先读 .env（服务器/多环境），未配置时回退本地开发默认值，本地开箱即用
const pool = mysql.createPool({
  host: process.env.DB_HOST || 'localhost',
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '000',
  database: process.env.DB_NAME || 'X',
  connectionLimit: 10, // 控制最大连接数
  waitForConnections: true
});

module.exports = { promisePool: pool };
