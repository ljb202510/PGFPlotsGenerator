const express = require('express');
const router = express.Router();
const axios = require('axios');
const dbModule = require('../db');
const db = dbModule.promisePool;
const { authenticateToken } = require('../middleware/auth');
const fs = require('fs');
const path = require('path');
const XLSX = require('xlsx'); // 添加Excel解析库
const OpenAI = require('openai');// 用于调用 Qwen（超算）等 OpenAI 兼容接口
const { writeSystemLog } = require('../utils/systemLog');

// 安装所需依赖：npm install xlsx

// 读取和解析文件内容的辅助函数，上限需要测试，目前为100MB未测试
const readFileContent = async (filePath, maxSize = 100000000) => {
  return new Promise((resolve, reject) => {
    // 检查文件是否存在
    if (!fs.existsSync(filePath)) {
      return reject(new Error('文件不存在'));
    }
    
    // 检查文件大小
    const stats = fs.statSync(filePath);
    if (stats.size > maxSize) {
      return reject(new Error(`文件过大，最大支持 ${maxSize} 字节`));
    }
    
    // 获取文件扩展名
    const ext = path.extname(filePath).toLowerCase();
    
    try {
      // 处理Excel文件
      if (ext === '.xlsx' || ext === '.xls') {
        // 使用xlsx库解析Excel文件
        const workbook = XLSX.readFile(filePath);
        const sheetName = workbook.SheetNames[0]; // 获取第一个工作表
        const worksheet = workbook.Sheets[sheetName];
        
        // 将Excel数据转换为JSON
        const jsonData = XLSX.utils.sheet_to_json(worksheet, { header: 1 });
        
        // 将数据格式化为易读的文本
        let formattedData = 'Excel文件内容（解析为表格格式）：\n';
        
        jsonData.forEach((row, index) => {
          formattedData += `行${index + 1}: ${JSON.stringify(row)}\n`;
        });
        
        resolve(formattedData);
      } 
      // 处理CSV文件
      else if (ext === '.csv') {
        fs.readFile(filePath, 'utf8', (err, data) => {
          if (err) {
            reject(err);
          } else {
            resolve(`CSV文件内容：\n${data}`);
          }
        });
      }
      // 处理文本文件
      else {
        fs.readFile(filePath, 'utf8', (err, data) => {
          if (err) {
            reject(err);
          } else {
            resolve(`文件内容：\n${data}`);
          }
        });
      }
    } catch (error) {
      writeSystemLog('error', `[CHAT] 解析文件失败: ${error.message}`);
      reject(new Error(`解析文件失败: ${error.message}`));
    }
  });
};

// 从 AI 回复中提取图表代码：优先匹配 ```latex/tex 围栏，再兜底裸 \begin{tikzpicture}（防截断导致围栏不闭合）
const extractChartCode = (aiReply) => {
  if (!aiReply || typeof aiReply !== 'string') {
    return '';
  }
  const fenced = aiReply.match(/```(?:latex|tex)?\s*([\s\S]*?)\s*```/);
  if (fenced) {
    return fenced[1].trim();
  }
  const bare = aiReply.match(/\\begin\{tikzpicture\}[\s\S]*?\\end\{tikzpicture\}/);
  if (bare) {
    return bare[0].trim();
  }
  return '';
};

// 构建包含数据集信息的消息
const buildMessagesWithDataset = async (userMessage, dataIds, userId) => {
  // 基础消息结构
  let messages = [
    {
      role: 'system',
      content: `你是一个专业的图表生成助手：请根据用户需求（及上传的数据文件）生成一段可直接在服务端编译渲染的 PGFPlots / TikZ 图表代码。

【上下文独立指令】（每次生成都必须遵守）
本次生成**仅依据当前用户消息、当前上传的数据文件、以及你已知的 PGFPlots 语法模板**；不要参考本对话历史中之前的图表类型、数据文件、提示词用词或之前生成的代码，不要让上一例子的上下文影响当前结果。

【输出内容边界】（最高优先级，必须遵守）
1. 只输出图表代码片段本身：整体以 \\begin{tikzpicture} 开头、\\end{tikzpicture} 结尾；如需坐标轴，则在其内部使用 \\begin{axis}…\\end{axis}。
2. 禁止输出任何文档脚手架：不得出现 \\documentclass、\\usepackage、\\begin{document}、\\end{document}、standalone 等导言区内容；服务端会统一套用文档外壳，并已预置宏包：pgfplots、pgf-pie（饼图可直接使用 \\pie 命令）、amsmath、amssymb、xcolor、fontspec、xeCJK（中文字体已配好）。
3. 代码放在 \`\`\`latex … \`\`\` 围栏内，围栏内不夹带解释文字，只放最终代码。

【图表渲染规则】（除非用户消息明确指定了数值/颜色/图表类型，否则必须遵守）
R1 图例不遮挡数据：不使用 legend pos=north west / north east；默认外置右侧 legend style={at={(1.03,0.5)}, anchor=west, draw=black, fill=white}；无法外置时放轴内右下 legend style={at={(0.98,0.02)}, anchor=south east, draw=black, fill=white}；禁止 legend to name=… 暂存后另处引用。
R2 轴外不写文字：禁止 \\node at (current bounding box.*)；数据来源等注记写在 \\end{axis} 之前，例：\\node[anchor=north west, font=\\scriptsize] at (axis description cs:0.0,-0.15) {数据来源：×××};（纯 TikZ 图如 \\pie，注记写在 \\end{tikzpicture} 之前并保持在图内）。
R3 数值标注与数据点标记必须成对出现：凡有 mark=* 等数据点标记，必须在轴选项中同时开启 nodes near coords，并用如下无边框、无底色、上对齐的样式键（唯一正确写法）：
   every node near coord/.append style={font=\\scriptsize, fill=none, draw=none, inner sep=1pt, anchor=south}
   数据点超过 12 个时可每 n 个标注一次并在代码注释中说明；禁止写无效键 nodes near coords style=…；禁止使用默认节点样式（白底黑框会盖住数据点）；禁止漏写数值标注。
   【单系列密集点扩展】：当单一系列（折线或柱状）在轴上的分类数 X≥8 时，必须让相邻点位的数值标注交错方向，避免全部 anchor=south 挤在同一条水平线上互相贴住。做法：把轴拆成两段 \\addplot——较高 y 值段保持 anchor=south（标上方），较低 y 值段改 anchor=north（标下方）。模板：
   \\addplot[blue, mark=*, thick, nodes near coords, every node near coord/.append style={font=\\scriptsize, fill=none, draw=none, inner sep=1pt, anchor=south}] coordinates { (A,90) (C,85) (E,80) (G,75) };
   \\addplot[blue, mark=*, thick, nodes near coords, every node near coord/.append style={font=\\scriptsize, fill=none, draw=none, inner sep=1pt, anchor=north}] coordinates { (B,20) (D,15) (F,10) (H,5) };
R4 数值与坐标轴单位、量级必须一致：ylabel 含缩略单位（如（万人）、（亿元））时，\\addplot 的 data 坐标值与 ymin、ymax 用同一标度；正确示例 ylabel={出生人口（万人）} 配 coordinates {(2019,1000)} 与 ymin=800、ymax=1200；原始值 ≥100000 时先除以 10000 换算成「万」级再入图，并在 ylabel 中注明对应单位。
R5 误差棒语法：数据**不含**误差/区间列时，**禁止**在轴或 \\addplot 中出现 error bars/.cd、y dir=both、y explicit 等参数（负例）。数据**真实包含**误差/区间列时，方可启用，且必须遵守以下语法（正例唯一正确写法）：
   • error bars/.cd 子键挂在 \\addplot 的 +[...] 语法上，不要放在 axis 选项里；
   • 坐标格式用 (x,y) +- (err_x, err_y)，**禁止**三元组 (x,y,err)；
   • 示例：
   \\addplot +[error bars/.cd, y dir=both, y explicit] coordinates {
     (方案 A,520) +- (0,28)
     (方案 B,568) +- (0,35)
     (方案 C,602) +- (0,22)
     (方案 D,585) +- (0,30)
   };
R6 单图结构：只输出一个 tikzpicture，禁止 figure、caption、\\ref、\\label。
R7 分类坐标轴 symbolic x coords 列表元素必须用英文半角逗号 , 分隔，绝对禁止全角中文逗号 ，：错误示例 symbolic x coords={一季度，二季度，三季度，四季度} 会被 pgfplots 当作单个分类，导致所有柱子全部挤到中间；正确示例 symbolic x coords={一季度,二季度,三季度,四季度}。坐标点内部分隔（如 (一季度,45)）不受此限。
R8 多系列折线/曲线（≥2 条系列且 X≥8 个点）的数值标注必须错开避免互相压盖：开启 nodes near coords 的每个系列必须单独设置标注样式；y 值总体较大的一条用 anchor=south（标在数据点上方），y 值总体较小的另一条用 anchor=north（标在数据点下方）；所有系列标注都必须 fill=none、draw=none、inner sep=1pt，字号建议 \\tiny 或 \\scriptsize。写法示例：
   \\addplot[blue, mark=*, thick, nodes near coords, every node near coord/.append style={font=\\tiny, fill=none, draw=none, inner sep=1pt, anchor=south}] coordinates {...};
   \\addplot[red, mark=*, thick, nodes near coords, every node near coord/.append style={font=\\tiny, fill=none, draw=none, inner sep=1pt, anchor=north}] coordinates {...};
禁止：同一 X、y 差很小的紧邻点位上两条系列都 anchor=south（或都 anchor=north）导致同侧叠加；禁止两条系列共用 axis 级同一个 every node near coord 样式而不做上下侧错开。

【进阶图型模板】（以下图型必须严格按模板写法，不要自行猜测语法）

• 堆叠柱状图（ybar stacked + 多系列）：用 **point meta=explicit symbolic + 坐标后加 [label]** 方式给每层柱段标注原始值（不是累积值）。这是 pgfplots 对堆叠柱段级标注唯一稳定的写法：
  \\begin{axis}[ ybar stacked, width=10cm, height=6cm,
    symbolic x coords={华北,华东,华南,西部}, xtick=data,
    ymin=0, ymax=100, enlarge x limits=0.15,
    legend style={...}
  ]
  \\addplot[fill=blue!50, nodes near coords, point meta=explicit symbolic,
            every node near coord/.append style={anchor=center, font=\\tiny, fill=none, draw=none}]
    coordinates {(华北,32)[32\\%] (华东,28)[28\\%] (华南,30)[30\\%] (西部,35)[35\\%]};
  \\addplot[fill=red!50, nodes near coords, point meta=explicit symbolic,
            every node near coord/.append style={anchor=center, font=\\tiny, fill=none, draw=none}]
    coordinates {(华北,26)[26\\%] (华东,30)[30\\%] ...};
  每个坐标对必须写成 (x, y)[原始值\\%] 或 (x, y)[原始值] 的形式（方括号内是要显示的标签文本）。
  **禁止**只写 (x, y) 不带 [label] 方括号，也禁止用 axis 级全局 nodes near coords（会标累积值不是原始值）。
  百分比堆叠柱**必须写 ymin=0, ymax=100**，否则顶层段的 anchor=center 标注会超出 y 轴上限被裁剪。

• 散点图（scatter / only marks，不要颜色或大小映射）：**禁止**在 axis 选项里写 scatter、scatter src=explicit（会导致 nodes near coords 被覆盖、数值标注消失）。正确做法是只用 only marks + mark=*，配合 axis 级 nodes near coords：
  \\begin{axis}[
    only marks,
    nodes near coords, every node near coord/.append style={...},
    mark=*, mark size=3pt,
    ...
  ]
  \\addplot coordinates { (5,42) (8,60) ... };

• ybar 边距（enlarge x limits）：所有柱状图一律写比例形式 \`enlarge x limits=0.15\`，写死常量、不涉及计算。**绝对禁止** abs 形式（如 \`{abs=0.3}\`、\`{abs=0.5}\`、\`{abs=N*0.15}\`）——AI 容易把公式当字面量写进代码（如 {abs=14*0.15}），pgfplots 不做算术运算会导致边距彻底失效。比例形式对 symbolic x coords 的中文分类名兼容性最好，分类数 4 或 14 都适用。

• 密集柱状图数值缩写（防长数字标注重叠）：当 ybar 分类数 ≥ 10，或数值位数长（5 位以上）且相邻柱数值接近时，柱顶标注的文本宽度不能超过相邻柱间距，否则长数字会互相压盖。此时用 point meta=explicit symbolic 把方括号里的显示标签缩写到 2–4 个字符：coordinates 里的 y 值仍写**真实完整数值**（保证柱高与 Y 轴尺度正确），只缩写方括号 [label] 文本。缩写单位必须与 Y 轴标签声明的单位一致——轴已标明单位（如 GDP/亿元）时只做数值缩写、**不要引入新单位或二次换算**。
  **硬约束（必须遵守）**：所有数据点必须放在**同一个 \\addplot** 里，全部统一用 anchor=south，**禁止为了让标注上下交错而拆成多个 \\addplot**——pgfplots 会把每个 \\addplot 当成一个独立数据系列、按系列数分配并排柱位，即使加 forget plot 也会导致柱子成对粘连、省份间空档错乱。缩写到 2–4 字符后标注宽度已小于柱间距，单一系列统一 anchor=south 即可，不需要交错。
  写法示例（单个 addplot）：
  \\addplot[fill=blue!50, nodes near coords, point meta=explicit symbolic,
            every node near coord/.append style={anchor=south, font=\\scriptsize, fill=none, draw=none}]
    coordinates {(广东,135673)[13.6万] (江苏,128222)[12.8万] (山东,92069)[9.2万] ...};
  （仅示例缩写手法；具体缩写成什么单位/保留几位小数，必须以该图 Y 轴标签的实际单位为准。）

【正例】（数值标注的正确写法，可直接参照）
\`\`\`latex
\\begin{tikzpicture}
\\begin{axis}[
    ybar,
    title={各车间产量对比},
    ylabel={产量（台）},
    symbolic x coords={一车间,二车间,三车间,四车间,五车间},
    xtick=data,
    nodes near coords,
    every node near coord/.append style={font=\\scriptsize, fill=none, draw=none, inner sep=1pt, anchor=south},
    legend style={at={(1.03,0.5)}, anchor=west}
]
\\addplot coordinates {(一车间,4520) (二车间,6100) (三车间,3890) (四车间,5230) (五车间,2980)};
\\end{axis}
\\end{tikzpicture}
\`\`\`

【反例】（禁止出现）
- \\documentclass{standalone}、\\usepackage{...}、\\begin{document} 等文档脚手架；
- nodes near coords style={...}（无效键，会导致标注带框）；
- 含数据点的图不写 nodes near coords（数值标注缺失）；
- 把数据来源等文字写在 axis 外、或 \\end{tikzpicture} 之后；
- symbolic x coords={一季度，二季度，…}（用全角中文逗号会视为单个分类，柱子全部挤到中间）；
- 多系列折线同 X 紧邻点都放同侧标注（如都 anchor=south）导致数值互相压盖、贴住线条。

【输出前自检】（逐条全部通过后再输出最终代码）
1 无任何文档脚手架与 \\usepackage；2 无 figure/caption/\\ref；3 每个含数据点的 addplot 都配 nodes near coords 与 every node near coord/.append style；4 数值单位与量级一致；5 图例不遮挡数据；6 数据来源注记在 \\end{axis}（纯 TikZ 为 \\end{tikzpicture}）之前；7 symbolic x coords 列表全用英文半角逗号分隔；8 多系列折线每个系列的 nodes near coords 已按 y 值大小分上下侧（anchor=south / anchor=north）错开；9 enlarge x limits 只能写比例形式 0.15，禁止 abs 形式（{abs=X}、{abs=N*0.15} 等一律不允许）；10 百分比堆叠柱必须写 ymin=0, ymax=100 且用 point meta=explicit symbolic + (x,y)[label] 方括号语法标注各层原始值；11 密集柱状图（分类≥10 或长数字且相邻值接近）用 point meta=explicit symbolic 把 [label] 缩写到 2–4 字符、坐标 y 值仍写真实值，缩写单位与 Y 轴标签一致、不二次换算；所有点必须在同一个 \\addplot 内统一 anchor=south，禁止拆成多个 \\addplot 做交错标注（会被当成多系列导致柱位错乱）。

如果用户上传的是 Excel/CSV 文件，我先将文件内容解析为表格格式提供给你。你需要：分析数据结构和内容 → 根据数据特点选择合适的图表类型 → 使用实际数据替换示例数据 → 设置合适的坐标轴标签、标题与图例。
若用户未提供数据文件：请基于你已知的公开权威统计数据自行生成真实图表代码，在标题或代码注释中注明年份，用真实数值替换示例数据。

【输出约定】最终代码必须放在 \`\`\`latex 代码块中，围栏内只含代码，不要返回文字说明或解释。`
    },
    {
      role: 'user',
      content: userMessage
    }
  ];
  
  if (!dataIds || dataIds.length === 0) {
    // 无数据集：引导模型基于公开数据自行生成代码，而非仅返回文字说明
    messages[0].content += '\n\n【本次请求】用户未上传数据集，请基于你已知的公开权威统计数据自行生成真实图表代码并注明年份，必须将代码放在 ```latex 代码块中。';
    return messages;
  }
  
  try {
    let datasetContent = '';
    
    // 查询数据集信息
    const query = `
      SELECT data_id, data_name, file_path, file_name 
      FROM data_file 
      WHERE data_id IN (?) AND user_id = ?
    `;
    
    const [datasets] = await db.query(query, [dataIds, userId]);
    
    if (datasets.length === 0) {
      console.log('未找到指定的数据集');
      return messages;
    }
    
    // 读取每个数据集的内容
    for (const dataset of datasets) {
      try {
        const content = await readFileContent(dataset.file_path);
        datasetContent += `\n\n=== 数据集: ${dataset.data_name} (${dataset.file_name}) ===\n${content}`;
      } catch (error) {
        console.error(`读取数据集 ${dataset.data_id} 失败:`, error.message);
        datasetContent += `\n\n=== 数据集: ${dataset.data_name} (${dataset.file_name}) ===\n[无法读取文件内容: ${error.message}]`;
      }
    }
    
    // 如果有数据集内容，将其作为系统消息的一部分
    if (datasetContent) {
      messages[0].content += `\n\n用户提供了以下数据文件内容：${datasetContent}`;
    }
    
    return messages;
    
  } catch (error) {
    console.error('构建数据集消息失败:', error);
    await writeSystemLog('error', `[CHAT] 构建数据集消息失败: ${error.message}`);
    return messages;
  }
};

// 保存历史记录到数据库和文件系统
const saveGenerationHistory = async (userId, userInput, aiResponse, dataIds, chartCode) => {
  try {
    // 获取数据集信息
    let dataId = null;
    let fileName = '';
    let datasetName = '';
    
    if (dataIds && dataIds.length > 0) {
      const query = `
        SELECT data_id, data_name, file_name 
        FROM data_file 
        WHERE data_id = ? AND user_id = ?
        LIMIT 1
      `;
      const [datasets] = await db.query(query, [dataIds[0], userId]);
      if (datasets.length > 0) {
        dataId = datasets[0].data_id;
        datasetName = datasets[0].data_name;
        fileName = datasets[0].file_name;
      }
    }
    
    // 构建生成描述
    const generationDescription = userInput ? 
      ` ${userInput.substring(0, 150)}${userInput.length > 150 ? '...' : ''}` :
      'AI图表生成';
    
    // 直接使用传入的 chartCode
    let finalChartCode = chartCode || '';
    
    // 若传入的 chartCode 为空，才从 aiResponse 提取（统一用 extractChartCode，保证与前端返回一致，绝不把散文当代码）
    if (!finalChartCode && aiResponse) {
      finalChartCode = extractChartCode(aiResponse);
    }
    
    console.log(`💾 保存图表代码，长度: ${finalChartCode.length}`);
    
    // 保存到数据库 generation_history 表
    const insertHistoryQuery = `
      INSERT INTO generation_history 
      (user_id, data_id, generation_description, generation_code, generation_path)
      VALUES (?, ?, ?, ?, ?)
    `;
    
    const [result] = await db.query(insertHistoryQuery, [
      userId,
      dataId,
      generationDescription,
      finalChartCode,
      null
    ]);
    
    // 获取自增的 history_id
    const historyId = result.insertId;
    
    console.log(`✅ 历史记录已保存到数据库，ID: ${historyId}`);
    
    // 保存到文件系统
    const historyDir = path.join(__dirname, '..', 'storage', 'history', userId.toString());
    
    if (!fs.existsSync(historyDir)) {
      fs.mkdirSync(historyDir, { recursive: true });
    }
    
    const historyFilePath = path.join(historyDir, `${historyId}.json`);
    const historyData = {
      history_id: historyId,
      user_id: userId,
      data_id: dataId,
      file_name: fileName,
      dataset_name: datasetName,
      user_input: userInput,
      ai_response: aiResponse,
      chart_code: finalChartCode,
      generation_description: generationDescription,
      created_at: new Date().toISOString(),
      metadata: {
        data_ids: dataIds || [],
        has_chart_code: !!finalChartCode,
        code_length: finalChartCode.length,
        code_source: chartCode ? 'from_request' : 'extracted_from_response'
      }
    };
    
    fs.writeFileSync(historyFilePath, JSON.stringify(historyData, null, 2));
    console.log(`✅ 历史记录已保存到文件系统: ${historyFilePath}`);
    
    // 记录API调用日志
    const apiLogQuery = `
      INSERT INTO api_log 
      (user_id, history_id, call_status, call_time)
      VALUES (?, ?, 'success', NOW())
    `;
    
    const [apiLogResult] = await db.query(apiLogQuery, [userId, historyId]);
    const callId = apiLogResult.insertId;
    
    console.log(`✅ API调用日志已记录，call_id: ${callId}`);
    
    return {
      historyId,
      callId,
      filePath: historyFilePath,
      chartCodeLength: finalChartCode.length
    };
    
  } catch (error) {
    console.error('保存历史记录失败:', error);
    await writeSystemLog('error', `[CHAT] 保存历史记录失败: ${error.message}`);
    return null;
  }
};

// 将一次对话的用户消息与 AI 消息持久化到 conversations / conversation_messages
const persistConversation = async (conversationId, userId, userMessage, aiReply, chartCode, historyId, selectedFiles) => {
  const conn = await db.getConnection();
  try {
    const [conv] = await conn.query(
      'SELECT user_id, title FROM conversations WHERE conversation_id = ?',
      [conversationId]
    );
    if (!conv.length || conv[0].user_id !== userId) {
      return; // 对话不存在或不属于当前用户，跳过持久化
    }

    await conn.query(
      'INSERT INTO conversation_messages (conversation_id, user_id, role, content, selected_files) VALUES (?, ?, ?, ?, ?)',
      [conversationId, userId, 'user', userMessage, JSON.stringify(selectedFiles || [])]
    );
    await conn.query(
      'INSERT INTO conversation_messages (conversation_id, user_id, role, content, chart_code, history_id) VALUES (?, ?, ?, ?, ?, ?)',
      [conversationId, userId, 'assistant', aiReply, chartCode || null, historyId || null]
    );

    // 首条消息自动生成标题（仅当仍为默认标题时）
    const currentTitle = conv[0].title;
    if (!currentTitle || currentTitle === '新对话') {
      const autoTitle = (userMessage || '').trim().substring(0, 20) || '新对话';
      await conn.query(
        'UPDATE conversations SET title = ? WHERE conversation_id = ?',
        [autoTitle, conversationId]
      );
    }
  } finally {
    conn.release();
  }
};

// AI 图表生成接口
router.post('/', authenticateToken, async (req, res) => {
  // 客户端断开/取消生成时联动中止上游 AI 调用（仅响应尚未结束时触发，避免误伤正常请求）
  const abortController = new AbortController();
  res.on('close', () => {
    if (!res.writableEnded) abortController.abort();
  });
  const signal = abortController.signal;

  try {
    const { message, data_ids, chart_code, model, conversation_id, selected_files } = req.body;

    if (!message) {
      return res.status(400).json({
        success: false,
        message: 'message字段是必需的'
      });
    }

    // 构建包含数据集的消息
    const messages = await buildMessagesWithDataset(
      message, 
      data_ids, 
      req.user.user_id
    );

    let aiReply;
    let usage;

    if (model === 'qwen') {
      // Qwen3.5 调用（超算）
      const NSCC_API_KEY = process.env.NSCC_API_KEY;
      const NSCC_API_URL = process.env.NSCC_API_URL;

      if (!NSCC_API_KEY) {
        return res.status(500).json({
          success: false,
          message: 'Qwen API密钥未配置'
        });
      }

      const client = new OpenAI({
        apiKey: NSCC_API_KEY,
        baseURL: NSCC_API_URL,
        timeout: 30000, // 与 DeepSeek 分支一致，避免请求无限挂起
      });

      const completion = await client.chat.completions.create({
        model: 'Qwen3.5',
        messages: messages,
        temperature: 0.7,
        max_tokens: 8192, // 提高输出预算，防止思考/长代码耗尽额度导致 content 为空
        stream: false,
        thinking: { type: 'disabled' } // 关闭深度思考（实测 enable_thinking 参数无效），避免思考耗尽预算后最终答案未产出
      }, { signal }); // signal：用户取消/断开时中止本次上游调用

      console.log(`用户 ${req.user.user_id} 调用了 Qwen3.5 API，附带数据集: ${data_ids || '无'}`);

      aiReply = completion.choices[0].message.content;
      usage = completion.usage;

      // 健壮性：content 为空/null 时记录原始响应并返回明确错误，绝不静默返回空
      if (!aiReply || typeof aiReply !== 'string' || !aiReply.trim()) {
        const emptyChoice = completion.choices?.[0];
        const emptyMsg = emptyChoice?.message || {};
        const emptyReasoning = emptyMsg.reasoning ?? emptyMsg.reasoning_content ?? '';
        console.error('⚠️ Qwen 返回内容为空，原始响应:', JSON.stringify(completion).slice(0, 1000));
        console.error('   finish_reason:', emptyChoice?.finish_reason, '| reasoning 长度:', String(emptyReasoning).length);
        await writeSystemLog('error', `[CHAT] Qwen 返回内容为空 (用户 ${req.user?.user_id || '未知'}) finish_reason=${emptyChoice?.finish_reason || '未知'}`);
        return res.status(502).json({
          success: false,
          message: 'AI 返回内容为空，请重试或检查模型配置'
        });
      }
    } else {
      // DeepSeek API配置
      const DEEPSEEK_API_URL = process.env.DEEPSEEK_API_URL;
      const DEEPSEEK_API_KEY = process.env.DEEPSEEK_API_KEY;

      if (!DEEPSEEK_API_KEY) {
        return res.status(500).json({
          success: false,
          message: 'DeepSeek API密钥未配置'
        });
      }

      // 调用DeepSeek API
      const response = await axios.post(
        DEEPSEEK_API_URL,
        {
          model: "deepseek-v4-flash",
          messages: messages,
          temperature: 0.7,
          max_tokens: 4096,
          stream: false
        },
        {
          headers: {
            'Authorization': `Bearer ${DEEPSEEK_API_KEY}`,
            'Content-Type': 'application/json'
          },
          timeout: 30000,
          signal // signal：用户取消/断开时中止本次上游调用
        }
      );

      console.log(`用户 ${req.user.user_id} 调用了DeepSeek API，附带数据集: ${data_ids || '无'}`);

      aiReply = response.data.choices[0]?.message?.content || '';
      usage = response.data.usage;

      // DeepSeek-V4-Flash 长推理时可能 reasoning_content 有完整代码但 content 为空（超时/截断）。
      // 三层降级：reasoning_content 兜底 → 同模型重试 1 次 → 切回 Qwen3.5 兜底
      if (!aiReply || !aiReply.trim()) {
        // Layer 1：从 reasoning_content 兜底提取代码块
        const reasoning = response.data.choices[0]?.message?.reasoning_content || '';
        const fenced = reasoning.match(/```(?:latex|tex)?\s*([\s\S]*?)\s*```/);
        if (fenced && fenced[1].trim()) {
          aiReply = `\`\`\`latex\n${fenced[1].trim()}\n\`\`\``;
          console.log('   ✅ Layer 1 命中：从 reasoning_content 提取到代码块，长度', aiReply.length);
        }
      }

      // Layer 2：同模型重发 1 次
      if (!aiReply || !aiReply.trim()) {
        try {
          console.log('   🔁 Layer 2：同模型重试 DeepSeek 1 次...');
          const retryResp = await axios.post(
            DEEPSEEK_API_URL,
            { model: 'deepseek-v4-flash', messages, temperature: 0.7, max_tokens: 4096, stream: false },
            { headers: { Authorization: `Bearer ${DEEPSEEK_API_KEY}`, 'Content-Type': 'application/json' }, timeout: 30000, signal }
          );
          aiReply = retryResp.data.choices[0]?.message?.content || '';
          if (!aiReply.trim()) {
            const r2 = retryResp.data.choices[0]?.message?.reasoning_content || '';
            const f2 = r2.match(/```(?:latex|tex)?\s*([\s\S]*?)\s*```/);
            if (f2 && f2[1].trim()) aiReply = `\`\`\`latex\n${f2[1].trim()}\n\`\`\``;
          }
        } catch (err) {
          console.error('   Layer 2 重试失败:', err.message);
        }
      }

      // Layer 3：切回 Qwen3.5 兜底
      if (!aiReply || !aiReply.trim()) {
        try {
          console.log('   🛟 Layer 3：切回 Qwen3.5 兜底...');
          const qwenResp = await client.chat.completions.create({
            model: process.env.QWEN_MODEL || 'qwen3.5',
            messages,
            temperature: 0.7,
            max_tokens: 4096,
            timeout: 30000,
            signal
          });
          aiReply = qwenResp.choices[0]?.message?.content || '';
          usage = qwenResp.usage;
          console.log('   ✅ Layer 3 命中：Qwen3.5 返回正常，长度', aiReply.length);
        } catch (err) {
          console.error('   Layer 3 Qwen 兜底也失败:', err.message);
        }
      }

      if (!aiReply || typeof aiReply !== 'string' || !aiReply.trim()) {
        console.error('⚠️ DeepSeek 三轮降级全部失败，原始响应:', JSON.stringify(response.data).slice(0, 1000));
        await writeSystemLog('error', `[CHAT] DeepSeek 内容为空 + 三层降级失败 (用户 ${req.user?.user_id || '未知'})`);
        return res.status(502).json({ success: false, message: 'AI 返回内容为空，请重试。' });
      }
    }

    // 提取图表代码（统一用 extractChartCode，保证与入库一致）
    let finalChartCode = chart_code || '';
    if (!finalChartCode && aiReply) {
      finalChartCode = extractChartCode(aiReply);
    }

    console.log('📊 图表代码处理过程:');
    console.log('最终提取的 finalChartCode 长度:', finalChartCode.length);

    // 用户已取消生成：不落库、不响应（避免产生用户不可见的孤立历史）
    if (signal.aborted) return;

    // 保存历史记录
    const savedHistory = await saveGenerationHistory(
      req.user.user_id,
      message,
      aiReply,
      data_ids,
      finalChartCode
    );

    // 持久化到对话（若前端传入 conversation_id）
    if (conversation_id) {
      await persistConversation(
        conversation_id,
        req.user.user_id,
        message,
        aiReply,
        finalChartCode,
        savedHistory ? savedHistory.historyId : null,
        selected_files
      );
    }

    // 返回响应
    res.json({
      success: true,
      data: {
        reply: aiReply,
        chart_code: finalChartCode,
        usage: usage,
        dataset_count: data_ids ? data_ids.length : 0,
        history_id: savedHistory ? savedHistory.historyId : null,
        chart_code_length: finalChartCode.length
      }
    });

  } catch (error) {
    // 用户主动中断生成：不写失败日志、不落库、不响应已断开连接
    if (signal.aborted || error.name === 'AbortError' || error.code === 'ERR_CANCELED') {
      console.log(`用户 ${req.user?.user_id || '未知'} 中断了图表生成请求`);
      return;
    }

    const modelName = req.body?.model === 'qwen' ? 'Qwen' : 'DeepSeek';
    console.error(`${modelName} API调用错误:`, error.response?.data || error.message);
    await writeSystemLog('error', `[CHAT] ${modelName} API调用错误: ${error.response?.data?.error?.message || error.message}`);
    
    // 记录失败的API调用
    try {
      if (req.user && req.user.user_id) {
        const errorMessage = error.response?.data?.error?.message || error.message || '未知错误';
        
        const apiLogQuery = `
          INSERT INTO api_log 
          (user_id, history_id, call_status, call_time, call_error)
          VALUES (?, NULL, 'failed', NOW(), ?)
        `;
        
        await db.query(apiLogQuery, [
          req.user.user_id,
          errorMessage.substring(0, 500)
        ]);

        console.log(`❌ API调用失败日志已记录，用户ID: ${req.user.user_id}`);
      }
    } catch (logError) {
      console.error('保存API失败日志时出错:', logError.message);
    }
    
    if (error.response) {
      res.status(error.response.status).json({
        success: false,
        message: `${modelName} API错误: ${error.response.data.error?.message || '未知错误'}`,
        error: error.response.data.error
      });
    } else if (error.request) {
      res.status(500).json({
        success: false,
        message: `无法连接到${modelName} API，请检查网络设置`
      });
    } else {
      res.status(500).json({
        success: false,
        message: '服务器内部错误: ' + error.message
      });
    }
  }
});


module.exports = router;
