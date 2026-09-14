package com.pg.pgfplots.service.rag;

import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.entity.RagVector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 批次1/A1 自检：Retriever 余弦相似度的确定性验证（同包可访问包级私有方法） */
class RetrieverTest {

    @Test
    void cosineIdenticalVectors() {
        float[] a = {1f, 2f, 3f};
        assertEquals(1.0, Retriever.cosine(a, a), 1e-9);
    }

    @Test
    void cosineOrthogonalVectors() {
        assertEquals(0.0, Retriever.cosine(new float[]{1f, 0f}, new float[]{0f, 1f}), 1e-9);
    }

    @Test
    void cosineZeroVectorIsSafe() {
        assertEquals(0.0, Retriever.cosine(new float[]{0f, 0f}, new float[]{1f, 1f}), 1e-9);
        assertFalse(Double.isNaN(Retriever.cosine(new float[]{0f, 0f}, new float[]{1f, 1f})));
    }

    // ---------------- [v1.4] 同题断链 ----------------

    /**
     * 闸门1：历史 embed_text 与提问文字相同（去首尾空白后）必须剔除。
     * <p>真实数据里 embed_text 是 {@code " 直方图"}（带前导空格），与查询 {@code "直方图"} 未必相似度 1.0，
     * 故字符串比较是必需的兜底 —— 这正是 317/323/339/340/341 五条重复案例的形成路径。</p>
     */
    @Test
    void dropsHistoryWhoseEmbedTextEqualsQuery() {
        VectorStore store = storeOf(
                List.of(row("history", 1, " 直方图", "[1.0,0.0]")),
                List.of());
        List<Retriever.Retrieved> hits = retrieverOf(store).retrieve(1, "直方图", new float[]{1f, 0f});
        assertTrue(hits.isEmpty(), "与提问文字相同的历史案例必须被剔除");
    }

    /** 闸门2：文字不同但相似度 ≥ 上限（改写过的同题）同样剔除。 */
    @Test
    void dropsHistoryScoringAboveCeiling() {
        VectorStore store = storeOf(
                List.of(row("history", 1, "画个直方图", "[1.0,0.0]")),
                List.of());
        List<Retriever.Retrieved> hits = retrieverOf(store).retrieve(1, "直方图", new float[]{1f, 0f});
        assertTrue(hits.isEmpty(), "相似度 ≥ maxHistoryScore 的历史案例应被剔除");
    }

    /** 相似的历史（0.9 < 0.98）保留；模板库即使相似度 1.0 也不受同题排除影响。 */
    @Test
    void keepsSimilarHistoryAndAllTemplates() {
        VectorStore store = storeOf(
                List.of(row("history", 1, "请根据我上传的数据绘制直方图，展示各分数段人数分布", "[0.9,0.43589]")),
                List.of(row("template", 2, "柱状图（ybar 数值标注）", "[1.0,0.0]")));
        List<Retriever.Retrieved> hits = retrieverOf(store).retrieve(1, "直方图", new float[]{1f, 0f});
        assertEquals(2, hits.size());
        assertTrue(hits.stream().anyMatch(h -> "history".equals(h.sourceType()) && h.refId() == 1),
                "相似但未达上限的历史案例应保留");
        assertTrue(hits.stream().anyMatch(h -> "template".equals(h.sourceType()) && h.refId() == 2),
                "模板库不应被同题排除影响");
    }

    /** query 为 null 时不得抛异常：闸门1（文字比较）不适用，闸门2（相似度上限）仍然生效。 */
    @Test
    void toleratesBlankQuery() {
        VectorStore store = storeOf(
                List.of(row("history", 1, " 直方图", "[0.9,0.43589]")),
                List.of());
        assertEquals(1, retrieverOf(store).retrieve(1, null, new float[]{1f, 0f}).size());
    }

    private static Retriever retrieverOf(VectorStore store) {
        AppProperties props = new AppProperties();
        props.getRag().setMinScore(0.5);
        props.getRag().setMaxHistoryScore(0.98);
        props.getRag().setTopKHistory(5);
        props.getRag().setTopKTemplate(5);
        return new Retriever(store, props);
    }

    /** 只返回指定行的 VectorStore 桩（不触碰 mapper） */
    private static VectorStore storeOf(List<RagVector> history, List<RagVector> templates) {
        return new VectorStore(null) {
            @Override
            public List<RagVector> loadHistory(Integer userId, int limit) {
                return history;
            }

            @Override
            public List<RagVector> loadTemplates(int limit) {
                return templates;
            }
        };
    }

    private static RagVector row(String sourceType, int refId, String embedText, String embedding) {
        RagVector row = new RagVector();
        row.setSourceType(sourceType);
        row.setRefId(refId);
        row.setTitle("示例" + refId);
        row.setEmbedText(embedText);
        row.setContent("需求：" + embedText);
        row.setEmbedding(embedding);
        return row;
    }
}
