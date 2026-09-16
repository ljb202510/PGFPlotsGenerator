package com.pg.pgfplots.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pg.pgfplots.client.LlmApiException;
import com.pg.pgfplots.client.LlmClient;
import com.pg.pgfplots.client.LlmConnectionException;
import com.pg.pgfplots.client.LlmResponse;
import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.common.ErrorTypes;
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
import com.pg.pgfplots.util.ChartCodeValidator;
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
 * <p>提示词组装 → 多通道生成（含 reasoning 兜底/同模型纠正重试/硬错误接力到备用通道）→ 代码提取 →
 * 空回复兜底 → 历史落库与 JSON 落盘 → 会话联动。</p>
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

    /** [批次3.5] 全局降级优先级：主通道失败后按此顺序接力，到链尾即止（不回绕）。 */
    private static final List<String> PROVIDER_CHAIN = List.of("qwen", "siliconflow", "deepseek");

    /** [批次3.5] 未知/空 model 的回落通道，与前端默认选项一致。 */
    private static final String DEFAULT_MODEL = "qwen";

    /** 生成图表（主流程）。 */
    public Map<String, Object> generate(Integer userId, ChatRequest request) {
        String message = request.getMessage();
        if (message == null || message.isEmpty()) {
            throw BusinessException.badRequest("message字段是必需的");
        }

        // [批次3.5] 单一映射：key → provider / 展示名 / 是否关思考 / 启用开关名；未知输入回落主通道
        ModelSpec spec = resolve(request.getModel());
        String requestedModel = spec.key();
        String label = spec.label();

        // 构建含数据集的系统提示词
        String systemPrompt = buildSystemPrompt(message, request.getDataIds(), userId);

        // [批次3.5] 停用优先于「缺 key」报错：前者是主动关闭（可恢复），后者是配置缺失，提示语不同。
        // 此处明确报错而不静默改用其它通道——否则用户会误以为自己用的是所选模型。
        if (!spec.provider().isEnabled()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, label + " 通道当前已停用，如需启用请将后端环境变量 "
                    + spec.enabledEnvKey() + " 设为 true 后重启服务");
        }
        if (spec.provider().getApiKey() == null || spec.provider().getApiKey().isEmpty()) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, label + " API密钥未配置");
        }

        String aiReply;
        var usage = (com.fasterxml.jackson.databind.JsonNode) null;
        // [批次3.5] 本次实际完成生成的通道；发生降级时与 requestedModel 不同，供响应与历史自证
        String usedModel = requestedModel;
        // [O1] LLM 段耗时（含降级链重试的全部时间），成功与失败两条路径都落 api_log.duration_ms
        long llmStart = System.nanoTime();
        try {
            FallbackResult result = generateWithFallback(systemPrompt, message, requestedModel);
            aiReply = result.reply();
            usage = result.usage();
            usedModel = result.usedModel();
        } catch (LlmApiException e) {
            log.error("[CHAT] {} API调用错误: {}", label, e.getMessage());
            recordFailedCall(userId, e.getMessage(), elapsedMs(llmStart), ErrorTypes.UPSTREAM_API_ERROR);
            systemLogWriter.error("[CHAT] " + label + " API调用错误: " + e.getMessage());
            throw new BusinessException(HttpStatus.resolve(e.getStatusCode()) != null
                    ? HttpStatus.valueOf(e.getStatusCode())
                    : HttpStatus.INTERNAL_SERVER_ERROR,
                    label + " API错误: " + e.getMessage());
        } catch (LlmConnectionException e) {
            log.error("[CHAT] {} API调用错误: {}", label, e.getMessage());
            recordFailedCall(userId, e.getMessage(), elapsedMs(llmStart), ErrorTypes.UPSTREAM_CONNECTION_ERROR);
            systemLogWriter.error("[CHAT] " + label + " API调用错误: " + e.getMessage());
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "无法连接到" + label + " API，请检查网络设置");
        } catch (Exception e) {
            log.error("[CHAT] {} API调用错误: {}", label, e.getMessage(), e);
            recordFailedCall(userId, e.getMessage(), elapsedMs(llmStart), ErrorTypes.UPSTREAM_UNKNOWN_ERROR);
            systemLogWriter.error("[CHAT] " + label + " API调用错误: " + e.getMessage());
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误: " + e.getMessage());
        }
        long llmDurationMs = elapsedMs(llmStart);

        log.info("用户 {} 调用了 {} API，附带数据集: {}", userId, label,
                request.getDataIds() == null ? "无" : request.getDataIds());

        // 空回复兜底
        if (aiReply == null || aiReply.trim().isEmpty()) {
            systemLogWriter.error("[CHAT] " + label + " 内容为空 + 降级全失败 (用户 " + userId + ")");
            // [E2] 补齐：空回复此前只写 system_log，未落 api_log，导致失败归类漏掉一整类
            recordFailedCall(userId, "AI 返回内容为空", elapsedMs(llmStart), ErrorTypes.EMPTY_REPLY);
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

        // [语料治理] 提取兜底收紧：结果必须含 tikz 画布，否则视为提取失败。
        // 根因：兜底提取过松，模型对「你是谁」这类非图表提问的闲聊回复也会被「提取」成代码，
        // 进而落库并索引进 RAG 向量库、被当作 few-shot 范例反复喂回（history 27/28/30… 事故）。
        // 放在此处（三条出口汇聚之后）而非提取器内部，是为了不影响 attemptOn 的纠正重试判定。
        if (finalChartCode != null && !finalChartCode.trim().isEmpty()
                && !ChartCodeValidator.hasTikzStructure(finalChartCode)) {
            finalChartCode = null;
        }

        // [E2] 生成链路走完但拿不到可编译代码 → 归为 CODE_EXTRACT_FAIL（单行落库，禁止双计）
        String errorType = (finalChartCode == null || finalChartCode.trim().isEmpty())
                ? ErrorTypes.CODE_EXTRACT_FAIL : null;

        // 保存历史（[O1] 带上 LLM 段耗时；[E2] 带上失败分类）
        SavedHistory saved = saveGenerationHistory(userId, message, aiReply, request.getDataIds(),
                finalChartCode, llmDurationMs, errorType, usedModel);

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
        data.put("chart_code_length", finalChartCode == null ? 0 : finalChartCode.length());
        // [批次3.5] 本次实际完成通道：与请求的 model 不同即表示发生过降级，供前端与评测自证
        data.put("model_used", usedModel);
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

    /**
     * [批次3.5] 降级链：主模型 → reasoning 兜底 / 同模型纠正重试 → 沿固定优先级接力到下一个可用通道。
     * <p>只有「换通道可能成功」的上游错误才接力（见 {@link #isDegradable}）；参数/鉴权类错误原样冒泡，
     * 避免把 45s 超时叠成 90s。走完链尾仍无内容时返回空回复，交由 {@code generate()} 统一判为
     * {@code EMPTY_REPLY}；若期间出现过异常则抛合并异常，状态码取首个异常，
     * 保证 {@code api_log.error_type} 的归类口径与改动前完全一致。</p>
     */
    private FallbackResult generateWithFallback(String systemPrompt, String userMessage, String requestedModel) {
        String primary = requestedModel;
        ModelSpec primarySpec = resolve(primary);
        // 各通道失败原因，用于降级日志与最终合并异常
        StringBuilder failures = new StringBuilder();
        LlmApiException firstApiError = null;
        LlmConnectionException firstConnError = null;

        // 1) 主通道
        FallbackResult attempt = null;
        try {
            attempt = attemptOn(primary, systemPrompt, userMessage);
        } catch (LlmApiException e) {
            if (!isDegradable(e.getStatusCode(), e.getMessage())) {
                throw e;   // 确定性错误（400/401/403…）换通道结果一样，直接原样冒泡
            }
            firstApiError = e;
            appendFailure(failures, primarySpec.label() + " HTTP " + e.getStatusCode() + ": " + e.getMessage());
        } catch (LlmConnectionException e) {
            firstConnError = e;
            appendFailure(failures, primarySpec.label() + " 连接失败: " + e.getMessage());
        }

        if (attempt != null) {
            if (!attempt.reply().trim().isEmpty()) {
                return attempt;
            }
            appendFailure(failures, primarySpec.label() + ": 内容为空");
        }

        // 2) 沿链尾方向逐个尝试备用通道（enabled=false 或未配置 key 的层整层跳过）
        for (String key : fallbackCandidatesOf(primary)) {
            ModelSpec spec = resolve(key);
            if (!isUsable(spec)) {
                continue;
            }
            try {
                FallbackResult fallback = attemptOn(key, systemPrompt, userMessage);
                if (!fallback.reply().trim().isEmpty()) {
                    log.warn("[CHAT] 降级成功：{} → {}（原因: {}）", primary, key, failures);
                    systemLogWriter.warning("[CHAT] 降级成功：" + primarySpec.label() + " → " + spec.label()
                            + "，原因: " + failures);
                    return fallback;
                }
                appendFailure(failures, spec.label() + ": 内容为空");
            } catch (LlmApiException e) {
                if (firstApiError == null) {
                    firstApiError = e;
                }
                appendFailure(failures, spec.label() + " HTTP " + e.getStatusCode() + ": " + e.getMessage());
            } catch (LlmConnectionException e) {
                if (firstConnError == null) {
                    firstConnError = e;
                }
                appendFailure(failures, spec.label() + " 连接失败: " + e.getMessage());
            }
        }

        // 3) 全链失败：有异常就抛合并异常（保留首个异常的状态码），否则交由空回复兜底
        if (firstApiError != null) {
            throw new LlmApiException(firstApiError.getStatusCode(), "所有通道均失败 → " + failures);
        }
        if (firstConnError != null) {
            throw new LlmConnectionException("所有通道均失败 → " + failures);
        }
        log.warn("[CHAT] 降级链走完仍无内容：{}", failures);
        return new FallbackResult("", null, primary);
    }

    /**
     * 对单个通道做一次完整尝试：调用 → reasoning 兜底 → 结构化校验失败则纠正重试 → 仍空再试一次。
     * <p>上游异常直接冒泡给 {@link #generateWithFallback} 判定是否接力。</p>
     */
    private FallbackResult attemptOn(String modelKey, String systemPrompt, String userMessage) {
        LlmResponse attempt = callOnce(modelKey, systemPrompt, userMessage);
        String reply = attempt.content();
        if (reply.trim().isEmpty()) {
            reply = ChartCodeExtractor.extractFencedBlock(attempt.reasoning());
        }

        // [A3] 结构化校验失败（JSON 无效且正则提不出代码）→ 带错误信息重试一次（同模型）
        if (!reply.trim().isEmpty() && ChartCodeExtractor.extract(reply).isEmpty()
                && StructuredOutputParser.parse(reply) == null) {
            String corrective = systemPrompt + "\n\n【上次输出无效】你上一次的回复既没有可解析的 JSON，"
                    + "也没有 ```latex 围栏代码。请严格按【结构化输出约定】重新输出。";
            LlmResponse retry = callOnce(modelKey, corrective, userMessage);
            String retryReply = retry.content();
            if (retryReply != null && !retryReply.trim().isEmpty()) {
                reply = retryReply;
                attempt = retry;
            }
        }

        if (reply.trim().isEmpty()) {
            attempt = callOnce(modelKey, systemPrompt, userMessage);
            reply = attempt.content();
            if (reply.trim().isEmpty()) {
                reply = ChartCodeExtractor.extractFencedBlock(attempt.reasoning());
            }
        }

        return new FallbackResult(reply, attempt.usage(), modelKey);
    }

    private LlmResponse callOnce(String modelKey, String systemPrompt, String userMessage) {
        ModelSpec spec = resolve(modelKey);
        return llmClient.chat(spec.provider(), systemPrompt, userMessage, spec.disableThinking());
    }

    /** 拼接失败原因，以 " | " 分隔。 */
    private static void appendFailure(StringBuilder failures, String text) {
        if (failures.length() > 0) {
            failures.append(" | ");
        }
        failures.append(text);
    }

    /** 保存生成历史（DB + JSON 落盘 + api_log）。失败返回 null，不影响主流程。 */
    private SavedHistory saveGenerationHistory(Integer userId, String userInput, String aiResponse,
                                               List<Integer> dataIds, String chartCode, Long durationMs,
                                               String errorType, String usedModel) {
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
            // [语料治理] 兜底提取结果同样必须含 tikz 画布，否则视为提取失败（不落库、不入向量索引）。
            // 这里是第二条独立路径：即使调用方已判为 null，本方法仍会从 aiResponse 再提取一次，
            // 若只改调用方，闲聊内容仍会经此路径落库并被索引（history 27/28/30… 事故根因之一）。
            if (!finalChartCode.isEmpty() && !ChartCodeValidator.hasTikzStructure(finalChartCode)) {
                log.warn("[CHAT] 兜底提取结果不含 tikz 画布，按提取失败处理（长度 {}）", finalChartCode.length());
                finalChartCode = "";
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
            // [批次3.5] 落盘实际完成通道，历史记录可自证是否降级（仅 JSON metadata，不改表结构）
            metadata.put("model_used", usedModel);
            historyData.put("metadata", metadata);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(historyFile.toFile(), historyData);
            log.info("✅ 历史记录已保存到文件系统: {}", historyFile);

            ApiLog apiLog = new ApiLog();
            apiLog.setUserId(userId);
            apiLog.setHistoryId(historyId);
            // [E2] 分类非空即失败：在同一条记录内决定状态，避免先写 success 再补 failed 造成双计
            apiLog.setCallStatus(errorType == null ? "success" : "failed");
            apiLog.setCallTime(LocalDateTime.now());
            // [A2] 记录提示词版本，便于同一用例换版本对比
            apiLog.setPromptVersion(PromptTemplates.VERSION);
            // [O1] LLM 调用耗时（毫秒），供 AdminLogService 聚合 avg/P95
            apiLog.setDurationMs(durationMs);
            if (errorType != null) {
                apiLog.setErrorType(errorType);
                // 让「最近失败调用」表的描述列有可读文案，而不是空白
                apiLog.setCallError("未从生成内容中提取到可编译的图表代码");
            }
            apiLogMapper.insert(apiLog);
            log.info("✅ API调用日志已记录，call_id: {}", apiLog.getCallId());

            // [RAG] 异步索引历史成功案例（fire-and-forget，内部全吞异常，不阻断主流程）
            if (historyId != null && !finalChartCode.isEmpty()) {
                // [语料治理] 只标「有无上传数据集」：无法区分「使用公开统计数据」与「模型自拟示意数据」
                String dataSource = (dataIds == null || dataIds.isEmpty()) ? "no-dataset" : "dataset";
                ragService.indexHistoryAsync(userId, historyId, generationDescription, finalChartCode, dataSource);
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

    /** 记录失败的 API 调用日志（[O1] 失败也落耗时；[E2] 带上失败分类）。 */
    private void recordFailedCall(Integer userId, String errorMessage, Long durationMs, String errorType) {
        try {
            String message = errorMessage == null ? "未知错误" : errorMessage;
            ApiLog apiLog = new ApiLog();
            apiLog.setUserId(userId);
            apiLog.setCallStatus("failed");
            apiLog.setCallTime(LocalDateTime.now());
            // [A2] 记录提示词版本，便于同一用例换版本对比
            apiLog.setPromptVersion(PromptTemplates.VERSION);
            apiLog.setDurationMs(durationMs);
            // [E2] 失败分类
            apiLog.setErrorType(errorType);
            apiLog.setCallError(message.length() > 500 ? message.substring(0, 500) : message);
            apiLogMapper.insert(apiLog);
            log.info("❌ API调用失败日志已记录，用户ID: {}", userId);
        } catch (Exception e) {
            log.error("保存API失败日志时出错: {}", e.getMessage());
        }
    }

    /** [O1] 纳秒计时转毫秒。 */
    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * [批次3.5] 单一映射：key → provider / 展示名 / 是否关闭思考 / 启用开关名。
     * <p>替代此前散落在 4 个方法里的二值三元判断。未知或空 model 回落主通道 qwen——
     * 旧的「非法输入一律落 deepseek」是危险默认，已纠正。</p>
     * <p>注意 disableThinking：只有 Qwen 需要（NSCC 模板层开关），GLM/DeepSeek 不认该参数，
     * 误传可能被上游判为非法参数而触发一次方向相反的降级。</p>
     */
    private ModelSpec resolve(String modelKey) {
        // 不能直接写 contains(modelKey)：List.of 的不可变列表对 null 会抛 NPE，
        // 而 model 是可选字段（旧客户端 / 直连调用可能不传），旧实现 "qwen".equals(model) 对 null 是安全的
        String key = (modelKey != null && PROVIDER_CHAIN.contains(modelKey)) ? modelKey : DEFAULT_MODEL;
        return switch (key) {
            case "siliconflow" -> new ModelSpec(key, appProperties.getLlm().getSiliconflow(),
                    "SiliconFlow", false, "SILICONFLOW_ENABLED");
            case "deepseek" -> new ModelSpec(key, appProperties.getLlm().getDeepseek(),
                    "DeepSeek", false, "DEEPSEEK_ENABLED");
            default -> new ModelSpec(key, appProperties.getLlm().getQwen(),
                    "Qwen", true, "QWEN_ENABLED");
        };
    }

    /**
     * [批次3.5] 槽位可用性 = 已启用且有 key。
     * <p>不可用则整层跳过，绝不用空 key 发请求——那会被上游回 401，被误记成「上游鉴权失败」。</p>
     */
    private boolean isUsable(ModelSpec spec) {
        return spec.provider().isEnabled()
                && spec.provider().getApiKey() != null && !spec.provider().getApiKey().isEmpty();
    }

    /** [批次3.5] 链上排在主通道之后的通道（不含主通道自身）；到链尾即止，不回绕。 */
    private List<String> fallbackCandidatesOf(String primaryKey) {
        int index = PROVIDER_CHAIN.indexOf(primaryKey);
        if (index < 0 || index >= PROVIDER_CHAIN.size() - 1) {
            return List.of();
        }
        return PROVIDER_CHAIN.subList(index + 1, PROVIDER_CHAIN.size());
    }

    /**
     * [批次3.5] 是否值得接力到下一通道：只救「换通道可能成功」的错误。
     * <ul>
     *   <li>可降级：402（余额/配额）、408、429（限流）、5xx（上游故障），以及消息含
     *       insufficient balance / quota / rate limit 的 4xx（部分厂商用 400 表达额度问题）</li>
     *   <li>不可降级：400 参数错误、401/403 鉴权失败等确定性错误——换通道结果一样，
     *       重试只会把单次超时叠成两倍</li>
     * </ul>
     */
    private static boolean isDegradable(int statusCode, String message) {
        if (statusCode == 402 || statusCode == 408 || statusCode == 429 || statusCode >= 500) {
            return true;
        }
        if (statusCode >= 400 && statusCode < 500) {
            return containsIgnoreCase(message, "insufficient balance")
                    || containsIgnoreCase(message, "quota")
                    || containsIgnoreCase(message, "rate limit");
        }
        return false;
    }

    private static boolean containsIgnoreCase(String text, String keyword) {
        return text != null && text.toLowerCase().contains(keyword);
    }

    /** [批次3.5] 通道路由规格：一处定义 key 到路由信息的对应关系。 */
    private record ModelSpec(String key, AppProperties.Provider provider, String label,
                             boolean disableThinking, String enabledEnvKey) {
    }

    /** [批次3.5] 含实际完成通道，供响应 data.model_used 与历史 metadata 落盘，使降级可被审计。 */
    private record FallbackResult(String reply, com.fasterxml.jackson.databind.JsonNode usage, String usedModel) {
    }

    private record SavedHistory(Integer historyId, Integer callId, String filePath, int chartCodeLength) {
    }
}
