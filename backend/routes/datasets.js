// routes/datasets.js
const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const dbModule = require('../db');
const db = dbModule.promisePool;
const fs = require('fs');
const { authenticateToken } = require('../middleware/auth');
const { writeSystemLog } = require('../utils/systemLog');

// 确保uploads目录存在
const uploadDir = 'uploads';
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
}

// 配置multer（文件上传中间件）
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    cb(null, uploadDir);
  },
  filename: function (req, file, cb) {
    // 生成唯一文件名
    const uniqueName = Date.now() + '-' + Math.round(Math.random() * 1E9) + path.extname(file.originalname);
    cb(null, uniqueName);
  }
});

const upload = multer({ 
  storage: storage,
  limits: {
    fileSize: 100 * 1024 * 1024 // 限制100MB
  }
});

// 文件大小格式化函数
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

// 数据集名称兜底：data_name 列上限 50，超长时截断；Array.from 避免截断代理对（如 emoji）
const DATA_NAME_MAX = 50;
const truncateName = (value) => {
  const s = String(value || '').trim();
  const chars = Array.from(s);
  return chars.length > DATA_NAME_MAX ? chars.slice(0, DATA_NAME_MAX).join('') : s;
};

// 获取所有数据集 - 添加认证
router.get('/', authenticateToken, async (req, res) => {
  const { keyword } = req.query;
  const user_id = req.user.user_id;
  
  try {
    let query = `
      SELECT data_id, user_id, data_name, data_size, description, 
             DATE_FORMAT(load_time, '%Y/%m/%d %H:%i') as upload_time,
             DATE_FORMAT(update_time, '%Y/%m/%d %H:%i:%s') as update_time,
             file_name, file_path, mimetype
      FROM data_file 
      WHERE user_id = ?
    `;
    const params = [user_id];
    
    if (keyword) {
      query += ' AND (data_name LIKE ? OR description LIKE ?)';
      const keywordPattern = `%${keyword}%`;
      params.push(keywordPattern, keywordPattern);
    }
    
    query += ' ORDER BY update_time DESC';
    
    const [rows] = await db.execute(query, params);
    
    // 格式化文件大小显示
    const formattedDatasets = rows.map(item => ({
      ...item,
      size: formatFileSize(item.data_size),
      count: 0
    }));
    
    res.json({
      code: 200,
      message: '获取成功',
      data: formattedDatasets
    });
    
  } catch (error) {
    console.error('获取数据失败:', error);
    await writeSystemLog('error', `[DATASET] 获取数据失败: ${error.message}`);
    res.status(500).json({
      code: 500,
      message: '获取数据失败: ' + error.message
    });
  }
});

// 创建数据集（上传文件和数据）- 添加认证
router.post('/', authenticateToken, upload.single('file'), async (req, res) => {
  const { name, description } = req.body;
  const user_id = req.user.user_id;
  const file = req.file;
  
  if (!name || !description) {
    if (file && fs.existsSync(file.path)) {
      fs.unlinkSync(file.path);
    }
    return res.status(400).json({
      code: 400,
      message: '数据集名称和描述不能为空'
    });
  }
  
  if (!file) {
    return res.status(400).json({
      code: 400,
      message: '请选择要上传的文件'
    });
  }

  // 名称兜底：trim 并截断到列上限，避免 “Data too long” 入库报错
  const dataName = truncateName(name);
  
  try {
    // 插入数据到数据库（不再插入load_time，使用默认值）
    const query = `
      INSERT INTO data_file (user_id, data_name, data_size, description, file_name, file_path, mimetype) 
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `;
    
    const [result] = await db.execute(query, [
      user_id,
      dataName,
      file.size,
      description,
      file.originalname,
      file.path,
      file.mimetype
    ]);
    
    // 获取插入的数据
    const [rows] = await db.execute(
      'SELECT * FROM data_file WHERE data_id = ?',
      [result.insertId]
    );
    
    const newDataset = {
      ...rows[0],
      size: formatFileSize(rows[0].data_size),
      uploadTime: new Date(rows[0].load_time).toLocaleDateString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      }),
      updateTime: new Date(rows[0].update_time).toLocaleString('zh-CN')
    };
    
    res.json({
      code: 200,
      message: '上传成功',
      data: newDataset
    });
    
  } catch (error) {
    console.error('上传失败:', error);
    await writeSystemLog('error', `[DATASET] 上传失败: ${error.message}`);
    if (file && fs.existsSync(file.path)) {
      fs.unlinkSync(file.path);
    }
    res.status(500).json({
      code: 500,
      message: '上传失败: ' + error.message
    });
  }
});

// 更新数据集 - 简化版本
router.post('/:id/update', authenticateToken, async (req, res) => {
  try {
    const { name, description } = req.body;
    const id = parseInt(req.params.id);
    const user_id = req.user.user_id;

    if (!name && !description) {
      return res.status(400).json({
        code: 400,
        message: '请提供数据集名称或描述进行更新'
      });
    }

    const updateFields = [];
    const params = [];

    if (name) {
      if (typeof name !== 'string' || name.trim() === '') {
        return res.status(400).json({
          code: 400,
          message: '数据集名称不能为空'
        });
      }
      updateFields.push('data_name = ?');
      params.push(truncateName(name));
    }

    if (description !== undefined) {
      updateFields.push('description = ?');
      params.push(description || null);
    }

    // 注意：update_time 字段会自动更新，不需要手动设置

    params.push(id, user_id);

    const query = `
      UPDATE data_file 
      SET ${updateFields.join(', ')} 
      WHERE data_id = ? AND user_id = ?
    `;

    const [result] = await db.execute(query, params);

    if (result.affectedRows === 0) {
      return res.status(404).json({
        code: 404,
        message: '数据集不存在或无权修改'
      });
    }

    // 获取更新后的数据
    const [rows] = await db.execute(
      'SELECT * FROM data_file WHERE data_id = ? AND user_id = ?',
      [id, user_id]
    );

    const updatedDataset = {
      ...rows[0],
      size: formatFileSize(rows[0].data_size),
      uploadTime: new Date(rows[0].load_time).toLocaleDateString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      }),
      updateTime: new Date(rows[0].update_time).toLocaleString('zh-CN')
    };

    res.json({
      code: 200,
      message: '更新成功',
      data: updatedDataset
    });

  } catch (error) {
    console.error('更新失败:', error);
    await writeSystemLog('error', `[DATASET] 更新失败: ${error.message}`);
    res.status(500).json({
      code: 500,
      message: '更新失败: ' + error.message
    });
  }
});

// 删除数据集 - 添加认证
router.delete('/:id', authenticateToken, async (req, res) => {
  const id = parseInt(req.params.id);
  const user_id = req.user.user_id;
  
  try {
    // 先查询数据集信息，包括文件路径
    const [rows] = await db.execute(
      'SELECT file_path, file_name FROM data_file WHERE data_id = ? AND user_id = ?',
      [id, user_id]
    );
    
    if (rows.length === 0) {
      return res.status(404).json({
        code: 404,
        message: '数据集不存在'
      });
    }
    
    const filePath = rows[0].file_path;
    
    // 删除数据库记录
    const [result] = await db.execute(
      'DELETE FROM data_file WHERE data_id = ? AND user_id = ?',
      [id, user_id]
    );
    
    // 删除对应的上传文件
    if (filePath && fs.existsSync(filePath)) {
      fs.unlinkSync(filePath);
      console.log(`已删除文件: ${filePath}`);
    }
    
    res.json({
      code: 200,
      message: '删除成功'
    });
    
  } catch (error) {
    console.error('删除失败:', error);
    await writeSystemLog('error', `[DATASET] 删除失败: ${error.message}`);
    res.status(500).json({
      code: 500,
      message: '删除失败: ' + error.message
    });
  }
});

// 下载文件 - 添加认证
router.get('/download/:id', authenticateToken, async (req, res) => {
  const id = parseInt(req.params.id);
  const user_id = req.user.user_id;
  
  try {
    const [rows] = await db.execute(
      'SELECT file_path, file_name FROM data_file WHERE data_id = ? AND user_id = ?',
      [id, user_id]
    );
    
    if (rows.length === 0) {
      return res.status(404).json({
        code: 404,
        message: '文件不存在'
      });
    }
    
    const dataset = rows[0];
    
    if (fs.existsSync(dataset.file_path)) {
      res.download(dataset.file_path, dataset.file_name);
    } else {
      res.status(404).json({
        code: 404,
        message: '文件不存在'
      });
    }
    
  } catch (error) {
    console.error('下载失败:', error);
    await writeSystemLog('error', `[DATASET] 下载失败: ${error.message}`);
    res.status(500).json({
      code: 500,
      message: '下载失败: ' + error.message
    });
  }
});

module.exports = router;