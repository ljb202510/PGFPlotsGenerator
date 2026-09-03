// routes/compile.js
const express = require('express');
const router = express.Router();
const dbModule = require('../db');
const db = dbModule.promisePool;
const { authenticateToken } = require('../middleware/auth');
const { writeSystemLog } = require('../utils/systemLog');
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');
const util = require('util');
const execPromise = util.promisify(exec);

// 获取PDF URL接口 这里构建的URL跟generation_path保持部分一致
router.get('/:history_id/pdf-url', authenticateToken, async (req, res) => {
  try {
    const { history_id } = req.params;
    const user_id = req.user.user_id;

    // 查询历史记录
    const query = `
      SELECT generation_path 
      FROM generation_history 
      WHERE history_id = ? AND user_id = ?
    `;

    const [records] = await db.query(query, [history_id, user_id]);

    if (records.length === 0) {
      return res.status(404).json({
        success: false,
        message: '历史记录不存在'
      });
    }

    const record = records[0];
    
    if (!record.generation_path) {
      return res.status(404).json({
        success: false,
        message: '该记录尚未生成PDF'
      });
    }

    // 构建完整的PDF URL
    const pdfUrl = `/storage/generated_charts/user${user_id}/hist${history_id}.pdf`;

    res.json({
      success: true,
      data: {
        pdf_url: pdfUrl,
        exists: fs.existsSync(path.join(__dirname, '..', '..', record.generation_path))
      }
    });

  } catch (error) {
    console.error('获取PDF URL失败:', error);
    await writeSystemLog('error', `[COMPILE] 获取PDF URL失败: ${error.message}`);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

// LaTeX编译接口 - 只保留核心功能
router.post('/:history_id', authenticateToken, async (req, res) => {
  let tempDir = null;
  
  try {
    const { history_id } = req.params;
    const user_id = req.user.user_id;

    // 验证history_id
    if (!history_id || isNaN(parseInt(history_id))) {
      return res.status(400).json({
        success: false,
        message: '无效的历史记录ID'
      });
    }

    // 查询历史记录
    const query = `
      SELECT 
        h.history_id,
        h.user_id,
        h.generation_code,
        h.generation_path
      FROM generation_history h
      WHERE h.history_id = ? AND h.user_id = ?
    `;

    const [records] = await db.query(query, [history_id, user_id]);

    if (records.length === 0) {
      return res.status(404).json({
        success: false,
        message: '历史记录不存在或无权访问'
      });
    }

    const historyRecord = records[0];

    // 检查是否有图表代码
    if (!historyRecord.generation_code || historyRecord.generation_code.trim() === '') {
      return res.status(400).json({
        success: false,
        message: '该历史记录没有可编译的图表代码'
      });
    }

    // 创建用户存储目录
    const userStorageDir = path.join(__dirname, '..', 'storage', 'generated_charts', `user${user_id}`);
    const finalDir = path.join(userStorageDir);
    
    // 确保目录存在
    if (!fs.existsSync(userStorageDir)) {
      fs.mkdirSync(userStorageDir, { recursive: true });
    }
    
    if (!fs.existsSync(finalDir)) {
      fs.mkdirSync(finalDir, { recursive: true });
    }

    // 创建临时工作目录
    tempDir = path.join(userStorageDir, `temp_${Date.now()}`);
    if (!fs.existsSync(tempDir)) {
      fs.mkdirSync(tempDir, { recursive: true });
    }

    // 预处理LaTeX代码 - 提取图表内容
    const processedLatexCode = preprocessLatexCode(historyRecord.generation_code);
    
    // 创建支持中文的LaTeX文档
    const latexDocument = createChineseLatexDocument(processedLatexCode);
    const texFileName = `hist${history_id}.tex`;  // 修改为hist前缀
    const texFilePath = path.join(tempDir, texFileName);

    // 写入tex文件
    fs.writeFileSync(texFilePath, latexDocument, 'utf8');

    // 使用XeLaTeX编译（支持中文）
    const compileResult = await compileLatexWithXeLaTeX(texFilePath, tempDir);

    // 检查是否生成了PDF
    const tempPdfPath = path.join(tempDir, `hist${history_id}.pdf`);  // 修改为hist前缀
    const pdfExists = fs.existsSync(tempPdfPath);

    // 在编译错误处理部分修改
    if (!pdfExists) {
      await safeCleanup(tempDir);
      return res.status(500).json({
        success: false,
        message: compileResult.stdout + compileResult.stderr
    });
    }

    // 将PDF文件移动到最终位置
    const finalPdfPath = path.join(finalDir, `hist${history_id}.pdf`);  // 修改为hist前缀
    
    if (fs.existsSync(finalPdfPath)) {
      fs.unlinkSync(finalPdfPath);
    }
    
    fs.renameSync(tempPdfPath, finalPdfPath);

    // 清理临时目录
    await safeCleanup(tempDir);

    // 更新数据库中的生成路径
    const updateQuery = `
      UPDATE generation_history 
      SET generation_path = ? 
      WHERE history_id = ? AND user_id = ?
    `;

    const relativePath = path.relative(path.join(__dirname, '..', '..'), finalPdfPath);
    await db.query(updateQuery, [relativePath, history_id, user_id]);

    // 返回成功响应
    res.json({
      success: true,
      message: '编译成功',
      data: {
        history_id: parseInt(history_id),
        pdf_path: relativePath,
        pdf_url: `/storage/generated_charts/user${user_id}/hist${history_id}.pdf`, // 添加访问URL
        file_size: fs.statSync(finalPdfPath).size
      }
    });

  } catch (error) {
    // 在catch错误处理部分修改
  if (tempDir && fs.existsSync(tempDir)) {
    await safeCleanup(tempDir);
  }

  console.error('编译错误:', error);
  await writeSystemLog('error', `[COMPILE] LaTeX 编译失败: ${error.message}`);
  res.status(500).json({
    success: false,
    message: error.message
  });
  }
});

// 预处理LaTeX代码 - 提取图表内容
function preprocessLatexCode(originalCode) {
  try {
    // 如果代码包含完整的文档结构，提取begin{document}和end{document}之间的内容
    if (originalCode.includes('\\begin{document}') && originalCode.includes('\\end{document}')) {
      const start = originalCode.indexOf('\\begin{document}') + '\\begin{document}'.length;
      const end = originalCode.indexOf('\\end{document}');
      const content = originalCode.substring(start, end).trim();
      
      // 清理可能的多余文档结构
      let cleanedContent = content
        .replace(/\\documentclass\{.*?\}/g, '')
        .replace(/\\usepackage.*?\{.*?\}/g, '')
        .replace(/\\begin\{document\}/g, '')
        .replace(/\\end\{document\}/g, '')
        .trim();
      
      if (cleanedContent) {
        return cleanedContent;
      }
    }
    
    return originalCode;
  } catch (error) {
    return originalCode;
  }
}

// 创建支持中文的LaTeX文档
function createChineseLatexDocument(chartCode) {
  return `\\documentclass[border=5pt]{standalone}
\\usepackage{pgfplots}
\\pgfplotsset{compat=1.18}
\\usepackage{amsmath}
\\usepackage{amssymb}
\\usepackage{xcolor}

% 支持中文
\\usepackage{fontspec}
\\usepackage{xeCJK}
\\setCJKmainfont{SimSun}
\\setmainfont{Times New Roman}

\\begin{document}

${chartCode}

\\end{document}`;
}

// 使用XeLaTeX编译（支持中文）
async function compileLatexWithXeLaTeX(texFilePath, outputDir) {
  try {
    const fileName = path.basename(texFilePath, '.tex');
    const dirName = path.dirname(texFilePath);

    const command = `cd "${dirName}" && xelatex -interaction=nonstopmode -output-directory="${outputDir}" "${texFilePath}"`;

    const { stdout, stderr } = await execPromise(command, { 
      timeout: 30000
    });

    const pdfPath = path.join(outputDir, `${fileName}.pdf`);
    const success = fs.existsSync(pdfPath);

    return {
      success: success,
      stdout: stdout,
      stderr: stderr
    };

  } catch (error) {
    const fileName = path.basename(texFilePath, '.tex');
    const pdfPath = path.join(outputDir, `${fileName}.pdf`);
    const pdfExists = fs.existsSync(pdfPath);
    
    return {
      success: pdfExists,
      stdout: error.stdout || '',
      stderr: error.stderr || ''
    };
  }
}

// 安全清理函数
async function safeCleanup(dirPath) {
  try {
    if (!fs.existsSync(dirPath)) return;
    
    const files = fs.readdirSync(dirPath);
    
    for (const file of files) {
      const filePath = path.join(dirPath, file);
      if (fs.statSync(filePath).isFile()) {
        try {
          fs.unlinkSync(filePath);
        } catch (error) {
          // 忽略删除错误
        }
      }
    }
    
    try {
      fs.rmdirSync(dirPath);
    } catch (error) {
      // 忽略目录删除错误
    }
  } catch (error) {
    // 忽略清理错误
  }
}

module.exports = router;