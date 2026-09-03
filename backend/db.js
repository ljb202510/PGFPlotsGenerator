// db.js - 共享连接池
const mysql = require('mysql2/promise');
const pool = mysql.createPool({
  host: 'localhost',
  user: 'root',
  password: '000',
  // 密码有改动
  database: 'X',
  connectionLimit: 10, // 控制最大连接数
  waitForConnections: true
});

module.exports = { promisePool: pool };


// 服务器版本，在宝塔面板里面修改了数据库名和密码
/* db.js - 共享连接池
const mysql = require('mysql2/promise');
const pool = mysql.createPool({
  host: 'localhost',
  user: 'mydb',
  password: 'mydb',
  database: 'X',
  connectionLimit: 10, // 控制最大连接数
  waitForConnections: true
});

module.exports = { promisePool: pool };
*/