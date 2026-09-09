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

// 鉴权获取 PDF 文件：校验历史归属后流式返回，替代原 /storage 无鉴权静态托管（防止越权枚举他人 PDF）
router.get('/:history_id/pdf', authenticateToken, async (req, res) => {
  try {
    const { history_id } = req.params;
    const user_id = req.user.user_id;

    if (!history_id || isNaN(parseInt(history_id))) {
      return res.status(400).json({
        success: false,
        message: '无效的历史记录ID'
      });
    }

    // 查询历史记录（校验归属）
    const query = `
      SELECT generation_path 
      FROM generation_history 
      WHERE history_id = ? AND user_id = ?
    `;

    const [records] = await db.query(query, [history_id, user_id]);

    if (records.length === 0) {
      return res.status(404).json({
        success: false,
        message: '历史记录不存在或无权访问'
      });
    }

    const record = records[0];

    if (!record.generation_path) {
      return res.status(404).json({
        success: false,
        message: '该记录尚未生成PDF'
      });
    }

    // 仅允许访问 storage 目录内的文件，防止路径穿越
    const absolutePath = path.resolve(__dirname, '..', '..', record.generation_path);
    const storageRoot = path.resolve(__dirname, '..', 'storage');

    if (!absolutePath.startsWith(storageRoot) || !fs.existsSync(absolutePath)) {
      return res.status(404).json({
        success: false,
        message: 'PDF文件不存在'
      });
    }

    res.sendFile(absolutePath);

  } catch (error) {
    console.error('获取PDF失败:', error);
    await writeSystemLog('error', `[COMPILE] 获取PDF失败: ${error.message}`);
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

    // 注入加固：编译前校验危险 LaTeX 序列（\write18 可执行系统命令、\input/\include 可读任意本地文件等）
    const latexCheck = validateLatexCode(processedLatexCode);
    if (!latexCheck.valid) {
      await safeCleanup(tempDir);
      return res.status(400).json({
        success: false,
        message: latexCheck.message
      });
    }

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
      // 保留失败样本供复检（仅提示文件位置，不输出具体报错）
      saveCompileFailure(history_id, texFilePath, compileResult);
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

    // 返回成功响应（PDF 不再暴露静态 URL，前端通过 GET /api/compile/:id/pdf 携带 JWT 获取 Blob 预览）
    res.json({
      success: true,
      message: '编译成功',
      data: {
        history_id: parseInt(history_id),
        pdf_path: relativePath,
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
    if (typeof originalCode !== 'string' || !originalCode.trim()) {
      return originalCode;
    }

    let code = originalCode;

    // 若包含完整文档结构，先截取 begin{document} 与 end{document} 之间的主体
    const docStart = code.indexOf('\\begin{document}');
    const docEnd = code.indexOf('\\end{document}');
    if (docStart !== -1 && docEnd !== -1 && docEnd > docStart) {
      code = code.substring(docStart + '\\begin{document}'.length, docEnd);
    }

    // 无论是否截取过，一律行级清除文档脚手架，防止双层 documentclass / 宏包重复引入
    const cleaned = code
      .replace(/\\documentclass(?:\[[^\]]*\])?\{[^}]*\}.*$/gm, '')
      .replace(/\\usepackage(?:\[[^\]]*\])?\{[^}]*\}.*$/gm, '')
      .replace(/\\begin\{document\}/g, '')
      .replace(/\\end\{document\}/g, '')
      .trim();

    // 清理结果为空时回退原始内容（保持容错，不破坏原样）
    return cleaned || originalCode;
  } catch (error) {
    return originalCode;
  }
}

// 创建支持中文的LaTeX文档
function createChineseLatexDocument(chartCode) {
  return `\\documentclass[border=5pt]{standalone}
\\usepackage{pgfplots}
\\usepackage{pgf-pie} % 饼图：AI 代码可直接使用 \\pie
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

// 编译前校验 LaTeX 代码：拦截可执行系统命令/读写本地文件/加载未知宏包的危险序列
function validateLatexCode(code) {
  if (!code || typeof code !== 'string') {
    return { valid: false, message: '图表代码为空，无法编译' };
  }
  if (code.length > 50000) {
    return { valid: false, message: '图表代码过长（超过 50000 字符），请重新生成后再试' };
  }

  const dangerousPatterns = [
    { pattern: /\\write18/, desc: '\\write18（执行系统命令）' },
    { pattern: /\\shellescape/, desc: '\\shellescape（shell 转义）' },
    { pattern: /\\openin/, desc: '\\openin（打开外部文件）' },
    { pattern: /\\input/, desc: '\\input（读取外部文件）' },
    { pattern: /\\include/, desc: '\\include（读取外部文件）' },
    { pattern: /\\read/, desc: '\\read（读取外部文件）' },
    { pattern: /\\includegraphics/, desc: '\\includegraphics（引用外部图片）' },
    { pattern: /\\usepackage/, desc: '\\usepackage（加载宏包）' },
    { pattern: /\\RequirePackage/, desc: '\\RequirePackage（加载宏包）' },
    { pattern: /\\lstinputlisting/, desc: '\\lstinputlisting（读取外部文件）' },
    { pattern: /\\verbatiminput/, desc: '\\verbatiminput（读取外部文件）' }
  ];

  for (const item of dangerousPatterns) {
    if (item.pattern.test(code)) {
      return { valid: false, message: `检测到不安全的 LaTeX 指令 ${item.desc}，已阻止编译` };
    }
  }

  return { valid: true, message: '' };
}

// 编译失败时保留失败样本供复检；控制台仅提示文件位置，不输出具体报错内容
function saveCompileFailure(historyId, texFilePath, compileResult) {
  try {
    if (!texFilePath || !fs.existsSync(texFilePath)) {
      return null;
    }
    const debugDir = path.join(__dirname, '..', 'storage', 'debug');
    if (!fs.existsSync(debugDir)) {
      fs.mkdirSync(debugDir, { recursive: true });
    }
    const base = `hist${historyId}_${Date.now()}`;
    const texTarget = path.join(debugDir, `${base}.tex`);
    fs.copyFileSync(texFilePath, texTarget);
    const logText =
      ((compileResult && compileResult.stdout) || '') +
      ((compileResult && compileResult.stderr) || '');
    if (logText) {
      fs.writeFileSync(path.join(debugDir, `${base}.log`), logText, 'utf8');
    }
    console.error(`[COMPILE] LaTeX 编译失败，失败样本已保存至: ${texTarget}`);
    return texTarget;
  } catch (error) {
    console.error(`[COMPILE] 保存编译失败样本出错: ${error.message}`);
    return null;
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