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

    // R7 后端化兜底：把 AI 可能误写的全角中文逗号「，」统一替换为英文半角逗号「,」。
    // 必须放在 docStart/docEnd 截取之前执行，否则截取后的子串可能遗漏原始位置的全角逗号。
    code = code.replace(/，/g, ',');

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
    const chartCode = cleaned || originalCode;

    // ========== 后处理（独立于 AI 生成质量，后端兜底） ==========
    // 设计原则：只保留零风险、不互相干扰的兜底；标注位置/旋转等交给 AI 自己写。
    //
    // P3 ybar 边距兜底：N≥7 时把任意 enlarge x limits（abs 形式或过小比例）统一替换为比例 0.15；
    // N≤6 不动 AI 代码（小分类数 AI 写的值通常正确）。
    const finalCode = fixYbarEnlargeLimits(chartCode);

    return finalCode;
  } catch (error) {
    return originalCode;
  }
}

// P1：标注-数据点/图形重叠修复。
// 问题：nodes near coords 的数值标注紧贴数据点（anchor=south 时标注在点正上方、点盖住文字底部），
// 或紧贴柱状图/面积图的图形（anchor=south 时标注在柱顶、与填充边界遮挡）。
// pgfplots 无法让 every node near coord 按点条件化位置，只能后端正则注入 yshift。
// 策略：扫描所有 every node near coord/.append style={...anchor=XXX...}，
//        若未含 yshift，对 anchor=south 注入 yshift=3pt（向上推，远离点/图形），
//        对 anchor=north 注入 yshift=-3pt（向下推，远离点/图形）。
//        若已含 yshift（AI 可能自己加了），不重复加。
function fixAnnotationOverlap(code) {
  const re = /every node near coord\/\.append style=\{([^}]*anchor=(south|north)[^}]*)\}/g;
  let out = code;
  out = out.replace(re, (full, body, anchor) => {
    if (/yshift\s*=/.test(body)) return full; // 已含 yshift，不重复
    const yshift = anchor === 'south' ? '3pt' : '-3pt';
    // 在 font=\scriptsize, 后面插入 yshift=3pt,
    const newBody = body.replace(/(font=(?:\\scriptsize|\\tiny|\\footnotesize))(?:\s*,\s*|\s*$)/, `$1, yshift=${yshift}, `);
    // 兜底：如果 body 里没有 font，在 body 开头加
    const finalBody = /font=/.test(newBody) ? newBody : `font=\\scriptsize, yshift=${yshift}, ${body}`;
    return `every node near coord/.append style={${finalBody}}`;
  });
  return out;
}

// P2：ybar stacked 堆叠柱状图——当前策略：**直接返回代码**（不做任何后处理）。
// 理由：之前试过移除 nodes near coords + 注入 \node at (axis cs:中文分类,值) 标注原始值，
// 但 axis cs 配合 symbolic x coords 的中文分类名在 xelatex 里有兼容问题，
// 导致整张图空白。AI 原始代码（纯 ybar stacked + fill）本身是标准 pgfplots 写法，
// 能正常编译渲染。让堆叠柱高度本身传达数据即可。
// TODO: 后续如果用户强烈需要堆叠柱带数值标注，考虑让 chat.js 模板里教 AI 用
// point meta=explicit symbolic + \node 手动标注，避免后端正则拼接的 axis cs 中文分类问题。
function fixStackedBarLabels(code) {
  return code; // 临时禁用所有后处理，让 AI 原始代码直接编译
}

// P3：ybar 边距兜底（简化版）
// 设计原则：不做任何按 N 的算术换算，只做"写死常量"的统一替换。
//   N ≤ 6  → 不动 AI 代码（小分类数 AI 写的值通常正确）
//   N ≥ 7  → 把任意 enlarge x limits 形式统一替换为比例 0.15：
//              · abs 形式 {abs=X} 或字面量 {abs=14*0.15} → enlarge x limits=0.15
//              · 比例形式 0.0X / 0.1X（<0.15）→ 0.15
//              · 没写 enlarge x limits 且有 ybar → 补 0.15
function fixYbarEnlargeLimits(code) {
  const symMatch = code.match(/symbolic\s+x\s+coords=\{([^}]+)\}/);
  if (!symMatch) return code;
  const N = symMatch[1].split(',').map(s => s.trim()).filter(Boolean).length;
  if (N <= 6) return code;

  let out = code;

  // abs 形式（含 AI 误写字面量 {abs=14*0.15}）→ 比例 0.15
  out = out.replace(/enlarge\s+x\s+limits=\{\s*abs=[^}]*\}/g, 'enlarge x limits=0.15');

  // 比例形式 < 0.15 → 0.15；已经是 0.15 或更大的保留
  out = out.replace(/enlarge\s+x\s+limits=\s*0\.(\d+)/g, (full, num) => {
    return parseFloat('0.' + num) < 0.15 ? 'enlarge x limits=0.15' : full;
  });

  // 没写且 ybar → 补
  if (!/enlarge\s+x\s+limits/.test(out) && /\bybar\b/.test(out)) {
    out = out.replace(/\bybar\b/, (m) => m + ', enlarge x limits=0.15');
  }

  return out;
}

// P4：X 轴刻度标签防重叠——当 symbolic x coords 分类数 N≥7 时，
// 如果代码里没写 rotate，自动注入 x tick label style={rotate=45, anchor=east, font=\small}。
function fixXTickLabelOverlap(code) {
  const symMatch = code.match(/symbolic\s+x\s+coords=\{([^}]+)\}/);
  if (!symMatch) return code;
  const N = symMatch[1].split(',').map(s => s.trim()).filter(Boolean).length;
  if (N < 7) return code;
  // 已经有 rotate 就不重复加
  if (/rotate\s*=/.test(code)) return code;

  // 注入到 ybar 或 axis 选项里最稳妥的位置：在 axis 开括号之后插入
  const inject = 'x tick label style={rotate=45, anchor=east, font=\\small}, ';
  // 在第一个 [ 之后插入，避开 \\addplot 的 [
  return code.replace(/\\begin\{axis\}\s*\[/, '\\begin{axis}[' + inject);
}

// P5：ybar 密集数据标签防水平重叠。
// 当 ybar 有 N≥10 个 symbolic x coords 且有 nodes near coords 时，
// 每个 every node near coord/.append style 注入 rotate=90，
// 让数据标签竖排渲染。水平方向 14 个 6 位数标签会严重互相遮挡，
// 竖排后沿 X 轴方向错开（每个标签占约 30pt，柱子宽度约 10pt → 10pt 间距足够）。
function fixYbarLabelHorizontalOverlap(code) {
  if (!/\bybar\b/.test(code)) return code;
  if (!/\bnodes near coords\b/.test(code)) return code;

  const symMatch = code.match(/symbolic\s+x\s+coords=\{([^}]+)\}/);
  if (!symMatch) return code;
  const N = symMatch[1].split(',').map(s => s.trim()).filter(Boolean).length;
  if (N < 10) return code;

  // 对 every node near coord/.append style={...} 注入 rotate=90（如果还没有的话）
  return code.replace(
    /every node near coord\/\.append style=\{([^}]*)\}/g,
    (full, body) => {
      if (/\brotate\s*=/.test(body)) return full; // 已有 rotate
      // 在 font=\scriptsize 后面插入 rotate=90,
      const newBody = body.replace(
        /(font=(?:\\scriptsize|\\tiny|\\footnotesize)(?:,\\scriptsize)*?)\s*,?\s*/,
        '$1, rotate=90, '
      );
      // 兜底：body 里没有 font=
      const finalBody = /font=/.test(newBody) ? newBody : `font=\\scriptsize, rotate=90, ${body}`;
      return `every node near coord/.append style={${finalBody}}`;
    }
  );
}

// 创建支持中文的LaTeX文档
function createChineseLatexDocument(chartCode) {
  return `\\documentclass[border=5pt]{standalone}
\\usepackage{pgfplots}
\\usepackage{pgf-pie} % 饼图：AI 代码可直接使用 \\pie
\\pgfplotsset{compat=1.18}
\\usepgfplotslibrary{fillbetween}  % 面积图 / 堆叠面积图 / \\closedcycle 填充路径
% 注：error bars 是 pgfplots 内置功能（在核心 pgfplots.errorbars.code.tex），不需要单独 \\usepgfplotslibrary 加载
\\usepackage{amsmath}
\\usepackage{amssymb}
\\usepackage{xcolor}[dvipsnames,svgnames]  % 加载标准色名表（steelblue/teal/orange/coral 等），避免 AI 用预定义色名时报 Undefined color

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