package com.pg.pgfplots.service.rag;

import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.entity.RagVector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * [RAG] 检索器：全量加载 → 暴力余弦 → 阈值过滤 → 分区 Top-k。
 * <p>数据量千级 × 1024 维 ≈ 百万级浮点乘加，毫秒级，无需 ANN/向量库；
 * 数据量上万时演进路径是替换 VectorStore 实现（接口不变），本类余弦逻辑仍可复用。</p>
 */
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
     * @param queryVec 查询向量（由 RagService 调 EmbeddingClient 得到）
     */
    public List<Retrieved> retrieve(Integer userId, float[] queryVec) {
        AppProperties.Rag rag = appProperties.getRag();
        List<Retrieved> hits = new ArrayList<>();
        hits.addAll(topK(vectorStore.loadHistory(userId, rag.getHistoryLimit()), queryVec, rag.getMinScore(), rag.getTopKHistory()));
        hits.addAll(topK(vectorStore.loadTemplates(rag.getTemplateLimit()), queryVec, rag.getMinScore(), rag.getTopKTemplate()));
        return hits;
    }

    private List<Retrieved> topK(List<RagVector> candidates, float[] queryVec, double minScore, int k) {
        List<Retrieved> scored = new ArrayList<>(candidates.size());
        for (RagVector row : candidates) {
            float[] vec = parse(row.getEmbedding());
            // dim 不符或脏数据行直接跳过（换 embedding 模型后的旧行）
            if (vec == null || vec.length != queryVec.length) {
                continue;
            }
            double score = cosine(queryVec, vec);
            if (score >= minScore) {
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
