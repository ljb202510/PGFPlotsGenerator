package com.pg.pgfplots.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pg.pgfplots.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [RAG] embedding 客户端：封装 OpenAI 兼容的 {@code POST {api-url}/embeddings}。
 * <p>HTTP 构建方式对齐 {@code client.LlmClient}（JDK HttpClient + RestClient）；
 * 超时独立取 app.rag.timeout-ms（3s，远小于 LLM 的 45s，保证召回失败不拖慢生成）。
 * 一切失败统一抛 {@link RagUnavailableException}，由 RagService 吞掉静默降级。</p>
 */
@Slf4j
@Component
public class EmbeddingClient {

    private final ObjectMapper objectMapper;
    private final AppProperties.Embedding embedding;
    private final RestClient restClient;

    public EmbeddingClient(AppProperties appProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.embedding = appProperties.getEmbedding();
        long timeout = appProperties.getRag().getTimeoutMs();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(timeout));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** 单条文本向量化 */
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /** 批量向量化；校验返回条数与 dim 一致 */
    public List<float[]> embedBatch(List<String> texts) {
        String url = normalizeBaseUrl(embedding.getApiUrl()) + "/embeddings";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", embedding.getModel());
        body.put("input", texts);

        String responseBody;
        try {
            // 按 byte[] 读取、再自行 UTF-8 解码：与 LlmClient 一致，不依赖上游 Content-Type
            byte[] raw = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + embedding.getApiKey())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            responseBody = raw == null ? "" : new String(raw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RagUnavailableException("[RAG] embedding 请求失败：" + e.getMessage(), e);
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != texts.size()) {
                throw new RagUnavailableException("[RAG] embeddings 返回条数不符：期望 " + texts.size()
                        + "，实际 " + (data.isArray() ? data.size() : "缺失"));
            }
            List<float[]> result = new ArrayList<>(texts.size());
            for (JsonNode item : data) {
                JsonNode arr = item.path("embedding");
                if (!arr.isArray() || arr.size() != embedding.getDim()) {
                    throw new RagUnavailableException("[RAG] embedding 维度不符：配置 dim=" + embedding.getDim()
                            + "，实际 " + (arr.isArray() ? arr.size() : "缺失"));
                }
                float[] vec = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    vec[i] = (float) arr.get(i).asDouble();
                }
                result.add(vec);
            }
            return result;
        } catch (RagUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new RagUnavailableException("[RAG] 解析 embedding 响应失败：" + e.getMessage(), e);
        }
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
