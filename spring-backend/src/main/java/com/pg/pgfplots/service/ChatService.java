package com.pg.pgfplots.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pg.pgfplots.client.LlmApiException;
import com.pg.pgfplots.client.LlmClient;
import com.pg.pgfplots.client.LlmConnectionException;
import com.pg.pgfplots.client.LlmResponse;
import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.dto.chat.ChatRequest;
import com.pg.pgfplots.entity.ApiLog;
import com.pg.pgfplots.entity.Conversation;
import com.pg.pgfplots.entity.ConversationMessage;
import com.pg.pgfplots.entity.DataFile;
import com.pg.pgfplots.entity.GenerationHistory;
import com.pg.pgfplots.mapper.ApiLogMapper;
import com.pg.pgfplots.mapper.ConversationMapper;
import com.pg.pgfplots.mapper.ConversationMessageMapper;
import com.pg.pgfplots.mapper.DataFileMapper;
import com.pg.pgfplots.mapper.GenerationHistoryMapper;
import com.pg.pgfplots.service.rag.RagService;
import com.pg.pgfplots.util.ChartCodeExtractor;
import com.pg.pgfplots.util.FileContentReader;
import com.pg.pgfplots.util.PromptTemplates;
import com.pg.pgfplots.util.StructuredOutputParser;
import com.pg.pgfplots.util.SystemLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 图表生成服务，对应 Node 的 {@code routes/chat.js}。
 * <p>提示词组装 → 双模型调用（含 reasoning 兜底/同模型重试/切换模型兜底）→ 代码提取 → 空回复兜底 →
 * 历史落库与 JSON 落盘 → 会话联动。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final LlmClient llmClient;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final DataFileMapper dataFileMapper;
    private final GenerationHistoryMapper historyMapper;
    private final ApiLogMapper apiLogMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final FileContentReader fileContentReader;
    private final SystemLogWriter systemLogWriter;
    private final RagService ragService;

    /** 生成图表（主流程）。 */
    public Map<String, Object> generate(Integer userId, ChatRequest request) {
        String message = request.getMessage();
        if (message == null || message.isEmpty()) {
            throw BusinessException.badRequest("message字段是必需的");
        }

        String requestedModel = normalizeModel(request.getModel());
        String label = labelOf(requestedModel);

        // 构建含数据集的系统提示词
        String systemPrompt = buildSystemPrompt(message, request.getDataIds(), userId);

        // 校验 API Key
        AppProperties.Provider provider = providerOf(requestedModel);
        if (provider.getApiKey() == null || provider.getApiKey().isEmpty()) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, label + " API密钥未配置");
        }

        String aiReply;
        var usage = (com.fasterxml.jackson.databind.JsonNode) null;
        try {
            FallbackResult result = generateWithFallback(systemPrompt, message, requestedModel);
            aiReply = result.reply();
            usage = result.usage();
        } catch (LlmApiException e) {
            log.error("[CHAT] {} API调用错误: {}", label, e.getMessage());
            recordFailedCall(userId, e.getMessage());
            systemLogWriter.error("[CHAT] " + label + " API调用错误: " + e.getMessage());
            throw new BusinessException(HttpStatus.resolve(e.getStatusCode()) != null
                    ? HttpStatus.valueOf(e.getStatusCode())
                    : HttpStatus.INTERNAL_SERVER_ERROR,
                    label + " API错误: " + e.getMessage());
        } catch (LlmConnectionException e) {
            log.error("[CHAT] {} API调用错误: {}", label, e.getMessage());
            recordFailedCall(userId, e.getMessage());
            systemLogWriter.error("[CHAT] " + label + " API调用错误: " + e.getMessage());
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "无法连接到" + label + " API，请检查网络设置");
        } catch (Exception e) {
            log.error("[CHAT] {} API调用错误: {}", label, e.getMessage(), e);
            recordFailedCall(userId, e.getMessage());
            systemLogWriter.error("[CHAT] " + label + " API调用错误: " + e.getMessage());
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误: " + e.getMessage());
        }

        log.info("用户 {} 调用了 {} API，附带数据集: {}", userId, label,
                request.getDataIds() == null ? "无" : request.getDataIds());

        // 空回复兜底
        if (aiReply == null || aiReply.trim().isEmpty()) {
            systemLogWriter.error("[CHAT] " + label + " 内容为空 + 降级全失败 (用户 " + userId + ")");
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 返回内容为空，请重试。");
        }

        // [A3] 解析顺序：JSON 结构化 → 前端显式传入 chart_code → 正则兜底（绝不因模型不听话而失败）
        StructuredOutputParser.Structured structured = StructuredOutputParser.parse(aiReply);
        String finalChartCode;
        if (structured != null) {
            finalChartCode = structured.code();
        } else if (request.getChartCode() != null && !request.getChartCode().isEmpty()) {
            finalChartCode = request.getChartCode();
        } else {
            finalChartCode = ChartCodeExtractor.extract(aiReply);
        }

        // 保存历史
        SavedHistory saved = saveGenerationHistory(userId, message, aiReply, request.getDataIds(), finalChartCode);

        // 会话联动
        if (request.getConversationId() != null) {
            persistConversation(request.getConversationId(), userId, message, aiReply, finalChartCode,
                    saved == null ? null : saved.historyId(), request.getSelectedFiles());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reply", aiReply);
        data.put("chart_code", finalChartCode);
        data.put("usage", usage);
        data.put("dataset_count", request.getDataIds() == null ? 0 : request.getDataIds().size());
        data.put("history_id", saved == null ? null : saved.historyId());
        data.put("chart_code_length", finalChartCode.length());
        // [A3] 结构化输出可用时附加（为 null 时不放入，保持旧客户端兼容）
        if (structured != null) {
            data.put("chart_type", structured.chartType());
            data.put("summary", structured.summary());
        }
        return data;
    }

    /** 构建含数据集内容的系统提示词。 */
    private String buildSystemPrompt(String userMessage, List<Integer> dataIds, Integer userId) {
        // [RAG] 召回 few-shot：关闭或失败时返回 ""，拼接结果与旧版逐字符一致（零破坏）
        // [A3] 追加结构化输出约定（解析失败由正则兜底，不影响旧链路）
        String fewShot = ragService.retrieveFewShot(userId, userMessage);
        StringBuilder content = new StringBuilder(
                PromptTemplates.SYSTEM_PROMPT + fewShot + PromptTemplates.STRUCTURED_OUTPUT_SUFFIX);
        if (dataIds == null || dataIds.isEmpty()) {
            content.append(PromptTemplates.NO_DATASET_SUFFIX);
            return content.toString();
        }

        try {
            List<DataFile> datasets = dataFileMapper.selectList(Wrappers.<DataFile>lambdaQuery()
                    .select(DataFile::getDataId, DataFile::getDataName, DataFile::getFilePath, DataFile::getFileName)
                    .in(DataFile::getDataId, dataIds)
                    .eq(DataFile::getUserId, userId));
            if (datasets.isEmpty()) {
                log.info("未找到指定的数据集");
                return content.toString();
            }

            StringBuilder datasetContent = new StringBuilder();
            for (DataFile dataset : datasets) {
                String header = "\n\n=== 数据集: " + dataset.getDataName() + " (" + dataset.getFileName() + ") ===\n";
                datasetContent.append(header);
                try {
                    datasetContent.append(fileContentReader.read(dataset.getFilePath()));
                } catch (Exception e) {
                    log.error("读取数据集 {} 失败: {}", dataset.getDataId(), e.getMessage());
                    datasetContent.append("[无法读取文件内容: ").append(e.getMessage()).append("]");
                }
            }
            if (!datasetContent.isEmpty()) {
                content.append("\n\n用户提供了以下文件内容：").append(datasetContent);
            }
        } catch (Exception e) {
            log.error("构建数据集消息失败: {}", e.getMessage());
            systemLogWriter.error("[CHAT] 构建数据集消息失败: " + e.getMessage());
        }
        return content.toString();
    }

    /** 降级链：主模型 → reasoning 兜底 → 同模型重试 → 切换另一模型。 */
    private FallbackResult generateWithFallback(String systemPrompt, String userMessage, String requestedModel) {
        String primary = requestedModel;
        String backup = "qwen".equals(primary) ? "deepseek" : "qwen";

        LlmResponse attempt = callOnce(primary, systemPrompt, userMessage);
        String reply = attempt.content();
        if (reply.trim().isEmpty()) {
            reply = ChartCodeExtractor.extractFencedBlock(attempt.reasoning());
        }

        // [A3] 结构化校验失败（JSON 无效且正则提不出代码）→ 带错误信息重试一次（同模型）
        if (!reply.trim().isEmpty() && ChartCodeExtractor.extract(reply).isEmpty()
                && StructuredOutputParser.parse(reply) == null) {
            String corrective = systemPrompt + "\n\n【上次输出无效】你上一次的回复既没有可解析的 JSON，"
                    + "也没有 ```latex 围栏代码。请严格按【结构化输出约定】重新输出。";
            LlmResponse retry = callOnce(primary, corrective, userMessage);
            String retryReply = retry.content();
            if (retryReply != null && !retryReply.trim().isEmpty()) {
                reply = retryReply;
                attempt = retry;
            }
        }

        if (reply.trim().isEmpty()) {
            attempt = callOnce(primary, systemPrompt, userMessage);
            reply = attempt.content();
            if (reply.trim().isEmpty()) {
                reply = ChartCodeExtractor.extractFencedBlock(attempt.reasoning());
            }
        }

        if (reply.trim().isEmpty()) {
            attempt = callOnce(backup, systemPrompt, userMessage);
            reply = attempt.content();
            if (reply.trim().isEmpty()) {
                reply = ChartCodeExtractor.extractFencedBlock(attempt.reasoning());
            }
        }

        return new FallbackResult(reply, attempt.usage());
    }

    private LlmResponse callOnce(String modelKey, String systemPrompt, String userMessage) {
        return llmClient.chat(providerOf(modelKey), systemPrompt, userMessage, disableThinkingOf(modelKey));
    }

    /** 保存生成历史（DB + JSON 落盘 + api_log）。失败返回 null，不影响主流程。 */
    private SavedHistory saveGenerationHistory(Integer userId, String userInput, String aiResponse,
                                               List<Integer> dataIds, String chartCode) {
        try {
            Integer dataId = null;
            String fileName = "";
            String datasetName = "";
            if (dataIds != null && !dataIds.isEmpty()) {
                DataFile dataset = dataFileMapper.selectOne(Wrappers.<DataFile>lambdaQuery()
                        .select(DataFile::getDataId, DataFile::getDataName, DataFile::getFileName)
                        .eq(DataFile::getDataId, dataIds.get(0))
                        .eq(DataFile::getUserId, userId)
                        .last("LIMIT 1"));
                if (dataset != null) {
                    dataId = dataset.getDataId();
                    datasetName = dataset.getDataName();
                    fileName = dataset.getFileName();
                }
            }

            String generationDescription = (userInput != null && !userInput.isEmpty())
                    ? " " + (userInput.length() > 150 ? userInput.substring(0, 150) + "..." : userInput)
                    : "AI图表生成";

            String finalChartCode = chartCode == null ? "" : chartCode;
            if (finalChartCode.isEmpty() && aiResponse != null) {
                finalChartCode = ChartCodeExtractor.extract(aiResponse);
            }
            log.info("💾 保存图表代码，长度: {}", finalChartCode.length());

            GenerationHistory history = new GenerationHistory();
            history.setUserId(userId);
            history.setDataId(dataId);
            history.setGenerationDescription(generationDescription);
            history.setGenerationCode(finalChartCode);
            history.setGenerationPath(null);
            historyMapper.insert(history);
            Integer historyId = history.getHistoryId();
            log.info("✅ 历史记录已保存到数据库，ID: {}", historyId);

            Path historyDir = Paths.get(appProperties.getStorage().getHistory())
                    .toAbsolutePath().normalize().resolve(String.valueOf(userId));
            Files.createDirectories(historyDir);
            Path historyFile = historyDir.resolve(historyId + ".json");

            Map<String, Object> historyData = new LinkedHashMap<>();
            historyData.put("history_id", historyId);
            historyData.put("user_id", userId);
            historyData.put("data_id", dataId);
            historyData.put("file_name", fileName);
            historyData.put("dataset_name", datasetName);
            historyData.put("user_input", userInput);
            historyData.put("ai_response", aiResponse);
            historyData.put("chart_code", finalChartCode);
            historyData.put("generation_description", generationDescription);
            historyData.put("created_at", Instant.now().truncatedTo(ChronoUnit.MILLIS).toString());
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("data_ids", dataIds == null ? List.of() : dataIds);
            metadata.put("has_chart_code", !finalChartCode.isEmpty());
            metadata.put("code_length", finalChartCode.length());
            metadata.put("code_source", (chartCode != null && !chartCode.isEmpty())
                    ? "from_request" : "extracted_from_response");
            historyData.put("metadata", metadata);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(historyFile.toFile(), historyData);
            log.info("✅ 历史记录已保存到文件系统: {}", historyFile);

            ApiLog apiLog = new ApiLog();
            apiLog.setUserId(userId);
            apiLog.setHistoryId(historyId);
            apiLog.setCallStatus("success");
            apiLog.setCallTime(LocalDateTime.now());
            // [A2] 记录提示词版本，便于同一用例换版本对比
            apiLog.setPromptVersion(PromptTemplates.VERSION);
            apiLogMapper.insert(apiLog);
            log.info("✅ API调用日志已记录，call_id: {}", apiLog.getCallId());

            // [RAG] 异步索引历史成功案例（fire-and-forget，内部全吞异常，不阻断主流程）
            if (historyId != null && finalChartCode != null && !finalChartCode.isEmpty()) {
                ragService.indexHistoryAsync(userId, historyId, generationDescription, finalChartCode);
            }

            return new SavedHistory(historyId, apiLog.getCallId(), historyFile.toString(), finalChartCode.length());
        } catch (Exception e) {
            log.error("保存历史记录失败: {}", e.getMessage());
            systemLogWriter.error("[CHAT] 保存历史记录失败: " + e.getMessage());
            return null;
        }
    }

    /** 会话消息持久化（会话不存在或不属于当前用户则跳过）。 */
    private void persistConversation(Integer conversationId, Integer userId, String userMessage,
                                     String aiReply, String chartCode, Integer historyId,
                                     List<Object> selectedFiles) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            return;
        }

        ConversationMessage userMsg = new ConversationMessage();
        userMsg.setConversationId(conversationId);
        userMsg.setUserId(userId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setSelectedFiles(writeJson(selectedFiles == null ? List.of() : selectedFiles));
        conversationMessageMapper.insert(userMsg);

        ConversationMessage assistantMsg = new ConversationMessage();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setUserId(userId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(aiReply);
        assistantMsg.setChartCode((chartCode == null || chartCode.isEmpty()) ? null : chartCode);
        assistantMsg.setHistoryId(historyId);
        conversationMessageMapper.insert(assistantMsg);

        String title = conversation.getTitle();
        if (title == null || title.isEmpty() || "新对话".equals(title)) {
            String trimmed = userMessage == null ? "" : userMessage.trim();
            String autoTitle = trimmed.length() > 20 ? trimmed.substring(0, 20) : trimmed;
            if (autoTitle.isEmpty()) {
                autoTitle = "新对话";
            }
            Conversation update = new Conversation();
            update.setConversationId(conversationId);
            update.setTitle(autoTitle);
            conversationMapper.updateById(update);
        }
    }

    /** 记录失败的 API 调用日志。 */
    private void recordFailedCall(Integer userId, String errorMessage) {
        try {
            String message = errorMessage == null ? "未知错误" : errorMessage;
            ApiLog apiLog = new ApiLog();
            apiLog.setUserId(userId);
            apiLog.setCallStatus("failed");
            apiLog.setCallTime(LocalDateTime.now());
            // [A2] 记录提示词版本，便于同一用例换版本对比
            apiLog.setPromptVersion(PromptTemplates.VERSION);
            apiLog.setCallError(message.length() > 500 ? message.substring(0, 500) : message);
            apiLogMapper.insert(apiLog);
            log.info("❌ API调用失败日志已记录，用户ID: {}", userId);
        } catch (Exception e) {
            log.error("保存API失败日志时出错: {}", e.getMessage());
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String normalizeModel(String model) {
        return "qwen".equals(model) ? "qwen" : "deepseek";
    }

    private AppProperties.Provider providerOf(String modelKey) {
        return "qwen".equals(modelKey) ? appProperties.getLlm().getQwen() : appProperties.getLlm().getDeepseek();
    }

    private boolean disableThinkingOf(String modelKey) {
        return "qwen".equals(modelKey);
    }

    private String labelOf(String modelKey) {
        return "qwen".equals(modelKey) ? "Qwen" : "DeepSeek";
    }

    private record FallbackResult(String reply, com.fasterxml.jackson.databind.JsonNode usage) {
    }

    private record SavedHistory(Integer historyId, Integer callId, String filePath, int chartCodeLength) {
    }
}
