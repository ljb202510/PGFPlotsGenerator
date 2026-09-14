package com.pg.pgfplots.service.rag;

import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.entity.RagVector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * [RAG] 检索器：全量加载 → 暴力余弦 → 阈值过滤 → 分区 Top-k。
 * <p>数据量千级 × 1024 维 ≈ 百万级浮点乘加，毫秒级，无需 ANN/向量库；
 * 数据量上万时演进路径是替换 VectorStore 实现（接口不变），本类余弦逻辑仍可复用。</p>
 * <p>[v1.4] 同题断链：历史分区增加两道闸门，排除「与当前提问几乎相同」的案例 ——
 * 否则同一句提问必然召回上一次的完整代码，模型直接照抄（含上一次的错误），形成自我强化循环。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Retriever {

    private final VectorStore vectorStore;
    private final AppProperties appProperties;

    /** 单条召回结果 */
    public record Retrieved(String sourceType, Integer refId, String title, String content, double score) {
    }

    /**
     * 检索入口：历史与模板分区独立 Top-k（互不挤占名额）。
     *
     * @param userId   当前用户（历史严格隔离）
     * @param query    用户原始消息（[v1.4] 用于排除同题历史案例）
     * @param queryVec 查询向量（由 RagService 调 EmbeddingClient 得到）
     */
    public List<Retrieved> retrieve(Integer userId, String query, float[] queryVec) {
        AppProperties.Rag rag = appProperties.getRag();
        List<Retrieved> hits = new ArrayList<>();
        // [v1.4] 闸门1（确定性）：文字与当前提问完全相同的历史案例剔除；
        // 闸门2（启发式）：相似度 ≥ maxHistoryScore 的同样剔除，兜住改写过的同题
        hits.addAll(topK(dropSameQuery(vectorStore.loadHistory(userId, rag.getHistoryLimit()), query),
                queryVec, rag.getMinScore(), rag.getMaxHistoryScore(), rag.getTopKHistory()));
        // 模板库是通用图型示范，描述文本与用户提问不会雷同，不受同题排除影响
        hits.addAll(topK(vectorStore.loadTemplates(rag.getTemplateLimit()), queryVec,
                rag.getMinScore(), Double.MAX_VALUE, rag.getTopKTemplate()));
        return hits;
    }

    /**
     * [v1.4] 剔除 embed_text 与当前提问（去首尾空白后）完全相同的历史案例。
     * <p>为什么不只靠相似度阈值：历史 embed_text 由 {@code " " + userInput} 生成、带一个前导空格，
     * 与用户输入未必相似度 1.0；字符串比较是零漏网的兜底。</p>
     */
    private static List<RagVector> dropSameQuery(List<RagVector> rows, String query) {
        if (query == null || rows.isEmpty()) {
            return rows;
        }
        String key = query.trim();
        List<RagVector> kept = new ArrayList<>(rows.size());
        int dropped = 0;
        for (RagVector row : rows) {
            String embedText = row.getEmbedText() == null ? "" : row.getEmbedText().trim();
            if (!key.isEmpty() && key.equals(embedText)) {
                dropped++;
            } else {
                kept.add(row);
            }
        }
        if (dropped > 0) {
            log.info("[RAG] 排除与当前提问文字相同的历史案例 {} 条", dropped);
        }
        return kept;
    }

    private List<Retrieved> topK(List<RagVector> candidates, float[] queryVec, double minScore,
                                 double maxScore, int k) {
        List<Retrieved> scored = new ArrayList<>(candidates.size());
        for (RagVector row : candidates) {
            float[] vec = parse(row.getEmbedding());
            // dim 不符或脏数据行直接跳过（换 embedding 模型后的旧行）
            if (vec == null || vec.length != queryVec.length) {
                continue;
            }
            double score = cosine(queryVec, vec);
            if (score >= minScore && score < maxScore) {
                scored.add(new Retrieved(row.getSourceType(), row.getRefId(), row.getTitle(), row.getContent(), score));
            }
        }
        scored.sort(Comparator.comparingDouble(Retrieved::score).reversed());
        return scored.subList(0, Math.min(k, scored.size()));
    }

    /** JSON 数组文本 → float[]；解析失败返回 null（该行视为脏数据跳过） */
    private float[] parse(String json) {
        try {
            String[] parts = json.substring(1, json.length() - 1).split(",");
            float[] vec = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vec[i] = Float.parseFloat(parts[i].trim());
            }
            return vec;
        } catch (Exception e) {
            return null;
        }
    }

    /** 余弦相似度（暴力双循环；无论是否归一化均成立） */
    static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
