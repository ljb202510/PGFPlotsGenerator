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
      content: `你是一个专业的图表生成助手，擅长根据用户需求和数据集生成LaTeX PGFPlots代码。

请根据用户的描述和上传的数据文件，生成相应的图表代码。

如果用户上传的是Excel文件，我会将文件内容解析为表格格式提供给你。你需要：
1. 分析数据结构和内容
2. 根据数据特点生成合适的图表
3. 使用实际数据替换模板中的示例数据
4. 设置合适的坐标轴标签、标题和图例

请直接生成包含实际数据的完整LaTeX代码。

【输出约定】必须将最终 PGFPlots 代码放在 \`\`\`latex 代码块中，不要只返回文字说明或解释；若用户未提供数据集，使用你已知的公开权威统计数据（如近五年出生人口）生成图表并注明年份，用真实数值替换示例数据。`
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
      });

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
          timeout: 30000
        }
      );

      console.log(`用户 ${req.user.user_id} 调用了DeepSeek API，附带数据集: ${data_ids || '无'}`);

      aiReply = response.data.choices[0].message.content;
      usage = response.data.usage;

      // 健壮性：content 为空/null 时记录原始响应并返回明确错误，绝不静默返回空
      if (!aiReply || typeof aiReply !== 'string' || !aiReply.trim()) {
        console.error('⚠️ DeepSeek 返回内容为空，原始响应:', JSON.stringify(response.data).slice(0, 1000));
        await writeSystemLog('error', `[CHAT] DeepSeek 返回内容为空 (用户 ${req.user?.user_id || '未知'})`);
        return res.status(502).json({
          success: false,
          message: 'AI 返回内容为空，请重试或检查模型配置'
        });
      }
    }

    // 提取图表代码（统一用 extractChartCode，保证与入库一致）
    let finalChartCode = chart_code || '';
    if (!finalChartCode && aiReply) {
      finalChartCode = extractChartCode(aiReply);
    }

    console.log('📊 图表代码处理过程:');
    console.log('最终提取的 finalChartCode 长度:', finalChartCode.length);

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
