// migrations/001_conversations.js
// 创建对话持久化所需的表：conversations / conversation_messages
const dbModule = require('../db');
const db = dbModule.promisePool;

async function main() {
  await db.query(`
    CREATE TABLE IF NOT EXISTS conversations (
      conversation_id INT AUTO_INCREMENT PRIMARY KEY,
      user_id INT NOT NULL,
      title VARCHAR(255) NOT NULL DEFAULT '新对话',
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      INDEX idx_conv_user (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  `);

  await db.query(`
    CREATE TABLE IF NOT EXISTS conversation_messages (
      message_id INT AUTO_INCREMENT PRIMARY KEY,
      conversation_id INT NOT NULL,
      user_id INT NOT NULL,
      role VARCHAR(20) NOT NULL,
      content TEXT,
      chart_code TEXT,
      history_id INT DEFAULT NULL,
      selected_files TEXT,
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_msg_conv (conversation_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  `);

  console.log('conversations tables ready');
  process.exit(0);
}

main().catch((e) => {
  console.error('migration failed:', e);
  process.exit(1);
});
