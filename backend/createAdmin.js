// generateHash.js
const bcrypt = require('bcryptjs');

async function generateHash() {
  const hash = await bcrypt.hash('666666', 10);
  console.log('密码哈希值:', hash);
  console.log('请复制这个哈希值用于 SQL 插入');
  //$2b$10$kkbK9FE66BSuZt.zEyitl.G8WqkK2yUmusQ53HWkFKRzLq..4iuc2
}

generateHash();