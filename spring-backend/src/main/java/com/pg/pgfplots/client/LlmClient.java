package com.pg.pgfplots.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pg.pgfplots.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 调用客户端：统一走 OpenAI 兼容的 {@code /chat/completions}。
 * <p>Qwen（NSCC）与 DeepSeek 仅配置不同（baseUrl/model/apiKey/是否关闭思考），复用同一实现。</p>
 */
@Slf4j
@Component
public class LlmClient {

    /** 日志/错误信息中响应体的最大展示长度 */
    private static final int BODY_SNIPPET_LIMIT = 1000;

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LlmClient(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        long timeout = appProperties.getLlm().getTimeoutMs();
        // 改用 JDK 内置 HttpClient（行为更接近 Node 的 fetch）：相比遗留的 HttpURLConnection，
        // 对分块/压缩/连接复用更稳健，避免读取 NSCC(Qwen) 响应体时中途失败。
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(timeout));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 调用一次模型。
     *
     * @param provider       模型提供方配置
     * @param systemPrompt   系统提示词（已含数据集内容）
     * @param userMessage    用户消息
     * @param disableThinking 是否关闭深度思考（Qwen3.5 需要）
     */
    public LlmResponse chat(AppProperties.Provider provider, String systemPrompt,
                            String userMessage, boolean disableThinking) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage));
        return chat(provider, messages, disableThinking);
    }

    /** 调用一次模型（显式消息列表）。 */
    public LlmResponse chat(AppProperties.Provider provider, List<Map<String, String>> messages,
                            boolean disableThinking) {
        String url = normalizeBaseUrl(provider.getApiUrl()) + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", provider.getModel());
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", appProperties.getLlm().getMaxTokens());
        body.put("stream", false);
        if (disableThinking) {
            // 关闭 Qwen 深度思考。实测（直连 NSCC 参数对照实验）：`thinking:{type:"disabled"}` 与顶层
            // `enable_thinking:false` 均被 NSCC 忽略（reasoning 仍 1 万+ 字符、耗时 ~42s，且偶尔思考跑满
            // max_tokens 只产出 "..." 残桩）；只有 Qwen 模板层开关 chat_template_kwargs 真正生效
            // （reasoning=0、耗时 2.1s、completion_tokens 284）。
            body.put("chat_template_kwargs", Map.of("enable_thinking", false));
        }

        String responseBody;
        try {
            // 按 byte[] 读取、再自行 UTF-8 解码：不依赖响应 Content-Type
            // （上游缺失该响应头时会被 Spring 退化为 application/octet-stream，导致 String 提取失败）。
            byte[] raw = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            responseBody = raw == null ? "" : new String(raw, StandardCharsets.UTF_8);
        } catch (HttpStatusCodeException e) {
            String upstreamBody = e.getResponseBodyAsString();
            MediaType contentType = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getContentType();
            log.error("[LLM] {} 上游返回 HTTP {} | url={} | contentType={} | body={}",
                    provider.getModel(), e.getStatusCode().value(), url, contentType, snippet(upstreamBody), e);
            throw new LlmApiException(e.getStatusCode().value(), extractUpstreamMessage(upstreamBody));
        } catch (ResourceAccessException e) {
            Throwable cause = rootCause(e);
            log.error("[LLM] {} 连接失败 | url={} | {}: {}",
                    provider.getModel(), url, cause.getClass().getSimpleName(), cause.getMessage(), e);
            throw new LlmConnectionException(cause.getMessage() == null ? e.getMessage() : cause.getMessage());
        } catch (RestClientException e) {
            // 读取响应体等 RestClient 层异常：此前会一路冒泡成「服务器内部错误」，这里带上上下文与完整堆栈
            Throwable cause = rootCause(e);
            String detail = "读取响应失败: " + cause.getClass().getSimpleName()
                    + (cause.getMessage() == null ? "" : ": " + cause.getMessage())
                    + " | url=" + url;
            log.error("[LLM] {} | {}", provider.getModel(), detail, e);
            throw new LlmApiException(500, detail);
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            String content = message.hasNonNull("content") ? message.get("content").asText("") : "";
            String reasoning = "";
            if (message.hasNonNull("reasoning")) {
                reasoning = message.get("reasoning").asText("");
            } else if (message.hasNonNull("reasoning_content")) {
                reasoning = message.get("reasoning_content").asText("");
            }
            // 输出被 max_tokens 截断时给出明确告警，避免静默产出残桩代码
            if ("length".equals(choice.path("finish_reason").asText(""))) {
                log.warn("[LLM] {} 输出被 max_tokens 截断 (finish_reason=length, completion_tokens={}) | url={}",
                        provider.getModel(), root.path("usage").path("completion_tokens").asInt(), url);
            }
            return new LlmResponse(content, reasoning, root.get("usage"));
        } catch (Exception e) {
            throw new LlmApiException(500, "解析模型响应失败: " + e.getMessage());
        }
    }

    private String extractUpstreamMessage(String body) {
        if (body == null || body.isEmpty()) {
            return "未知错误";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode message = node.path("error").path("message");
            if (message.isMissingNode() || message.isNull()) {
                return body;
            }
            return message.asText();
        } catch (Exception e) {
            return body;
        }
    }

    /** 响应体摘要，用于日志与错误信息，避免超长内容刷屏。 */
    private String snippet(String body) {
        if (body == null || body.isEmpty()) {
            return "<empty>";
        }
        return body.length() > BODY_SNIPPET_LIMIT
                ? body.substring(0, BODY_SNIPPET_LIMIT) + "...(truncated)"
                : body;
    }

    /** 取最底层异常原因，便于日志中直接看到真实原因（如读超时、连接重置）。 */
    private Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
