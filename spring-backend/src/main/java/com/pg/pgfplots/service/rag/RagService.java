package com.pg.pgfplots.service.rag;

import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.util.ChartCodeValidator;
import com.pg.pgfplots.util.SystemLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * [RAG] 检索增强门面（批次1/A1）。ChatService 唯一依赖点。
 * <p>降级规则：enabled=false → 立即返回 ""/忽略（零开销）；embedding 未配置 / 超时 / 报错 /
 * 维度不符 → systemLogWriter.warning + log.warn，返回 ""（主流程零影响，绝不外抛）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final AppProperties appProperties;
    private final EmbeddingClient embeddingClient;
    private final Retriever retriever;
    private final VectorStore vectorStore;
    private final SystemLogWriter systemLogWriter;

    /**
     * 生成前召回 few-shot 段落。任何失败返回 ""，调用方直接跳过拼接。
     *
     * @param userId 当前用户（历史隔离）
     * @param query  用户原始消息
     */
    public String retrieveFewShot(Integer userId, String query) {
        if (!appProperties.getRag().isEnabled() || query == null || query.isBlank()) {
            return "";
        }
        // 未配置 embedding 即降级（不视为错误，首次启动尚未配置属正常态）
        if (isBlank(appProperties.getEmbedding().getApiKey())) {
            return "";
        }
        try {
            float[] queryVec = embeddingClient.embed(query);
            List<Retriever.Retrieved> hits = retriever.retrieve(userId, query, queryVec);
            String fewShot = PromptComposer.compose(hits);
            log.info("[RAG] 召回 {} 条（user={}）", hits.size(), userId);
            return fewShot;
        } catch (Exception e) {
            log.warn("[RAG] 召回失败，静默降级：{}", e.getMessage());
            systemLogWriter.warning("[RAG] 召回失败，已降级：" + e.getMessage());
            return "";
        }
    }

    /**
     * 生成成功后异步索引历史案例（fire-and-forget：异常全吞，绝不上抛）。
     * 只索引 chartCode 非空的成功生成；embed_text = 用户需求描述，content = 需求 + tikz 代码。
     */
    @Async("ragIndexExecutor")
    public void indexHistoryAsync(Integer userId, Integer historyId, String description, String chartCode) {
        try {
            if (!appProperties.getRag().isEnabled() || historyId == null
                    || chartCode == null || chartCode.isBlank()) {
                return;
            }
            if (isBlank(appProperties.getEmbedding().getApiKey())) {
                return;
            }
            // [v1.2] 准入校验：两个 addplot 坐标完全相同的图属明显错误，绝不能进向量库——
            // 否则下次同题请求会把它当 few-shot 范例照抄，形成自我强化循环（history 315/318/329 事故）
            if (ChartCodeValidator.hasDuplicateSeries(chartCode)) {
                log.warn("[RAG] 跳过索引：多系列坐标完全相同，historyId={}", historyId);
                systemLogWriter.warning("[RAG] 跳过索引（多系列坐标重复）historyId=" + historyId);
                return;
            }
            String embedText = description == null || description.isBlank() ? "AI图表生成" : description;
            String content = "需求：" + embedText + "\n代码：\n" + chartCode;
            // title 用于演示与 few-shot 标注：取需求描述截断
            String title = embedText.length() > 50 ? embedText.substring(0, 50) + "..." : embedText;
            float[] vec = embeddingClient.embed(embedText);
            vectorStore.upsertHistory(userId, historyId, title, embedText, content, vec,
                    appProperties.getEmbedding().getModel(), appProperties.getEmbedding().getDim());
            log.info("[RAG] 历史案例已索引 historyId={} user={}", historyId, userId);
        } catch (Exception e) {
            log.warn("[RAG] 索引失败（忽略）：historyId={}，{}", historyId, e.getMessage());
            systemLogWriter.warning("[RAG] 索引失败 historyId=" + historyId + "：" + e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
