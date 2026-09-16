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

    // ---------------- [语料治理] 质量等级过滤 ----------------

    /**
     * 未定级（unverified）的历史案例不参与召回；模板分区不受等级过滤影响。
     * <p>注意：必须存在至少一条 verified，过滤后结果非空，才能观察「不触发回退」的严格过滤行为。</p>
     */
    @Test
    void filtersOutUnverifiedHistoryButKeepsTemplates() {
        VectorStore store = storeOf(
                List.of(rowWithQuality("history", 1, "请根据我上传的数据绘制直方图，展示各分数段人数分布", "[0.9,0.43589]",
                                RagQuality.UNVERIFIED),
                        rowWithQuality("history", 3, "绘制各分数段人数的直方图分布情况", "[0.85,0.52678]",
                                RagQuality.VERIFIED)),
                List.of(rowWithQuality("template", 2, "柱状图（ybar 数值标注）", "[1.0,0.0]", RagQuality.GOLDEN)));
        List<Retriever.Retrieved> hits = retrieverOf(store).retrieve(1, "直方图", new float[]{1f, 0f});
        assertFalse(hits.stream().anyMatch(h -> h.refId() == 1), "unverified 历史不应被召回");
        assertTrue(hits.stream().anyMatch(h -> h.refId() == 3), "verified 历史应被召回");
        assertTrue(hits.stream().anyMatch(h -> "template".equals(h.sourceType())), "模板分区不受等级过滤影响");
    }

    /** 已定级（verified）的历史案例正常参与召回。 */
    @Test
    void keepsVerifiedHistory() {
        VectorStore store = storeOf(
                List.of(rowWithQuality("history", 1, "请根据我上传的数据绘制直方图，展示各分数段人数分布", "[0.9,0.43589]",
                        RagQuality.VERIFIED)),
                List.of());
        assertEquals(1, retrieverOf(store).retrieve(1, "直方图", new float[]{1f, 0f}).size());
    }

    /**
     * 过滤后历史分区为空时必须回退为不过滤，绝不把检索变哑。
     * <p>典型场景：演示账号尚未执行离线定级，若直接过滤会导致可用案例全部召回不到。</p>
     */
    @Test
    void fallsBackWhenAllHistoryFilteredOut() {
        VectorStore store = storeOf(
                List.of(rowWithQuality("history", 1, "请根据我上传的数据绘制直方图，展示各分数段人数分布", "[0.9,0.43589]",
                        RagQuality.UNVERIFIED)),
                List.of());
        assertEquals(1, retrieverOf(store).retrieve(1, "直方图", new float[]{1f, 0f}).size(),
                "过滤后为空应回退为不过滤");
    }

    /** 门槛设为 unverified 时等价于不过滤（一键回退开关）。 */
    @Test
    void unverifiedThresholdDisablesFiltering() {
        VectorStore store = storeOf(
                List.of(rowWithQuality("history", 1, "请根据我上传的数据绘制直方图，展示各分数段人数分布", "[0.9,0.43589]",
                        RagQuality.UNVERIFIED)),
                List.of());
        AppProperties props = props();
        props.getRag().setMinQuality(RagQuality.UNVERIFIED);
        assertEquals(1, new Retriever(store, props).retrieve(1, "直方图", new float[]{1f, 0f}).size());
    }

    /** 等级为 null（迁移前遗留行 / 未知值）按最低级处理 → 不召回。 */
    @Test
    void treatsUnknownQualityAsLowest() {
        VectorStore store = storeOf(
                List.of(row("history", 1, " 直方图", "[1.0,0.0]"),
                        row("history", 2, "请根据我上传的数据绘制直方图，展示各分数段人数分布", "[0.9,0.43589]")),
                List.of());
        // 两条都是 null 等级：若只按严格过滤则全被剔除 → 触发回退（证明 null 不被当作高等级）
        List<Retriever.Retrieved> hits = retrieverOf(store).retrieve(1, "直方图", new float[]{1f, 0f});
        assertFalse(RagQuality.recallable(null, RagQuality.VERIFIED));
        assertEquals(1, hits.size(), "null 等级不算可召回，但回退后仍返回可用案例（不含同题）");
    }

    private static Retriever retrieverOf(VectorStore store) {
        return new Retriever(store, props());
    }

    private static AppProperties props() {
        AppProperties props = new AppProperties();
        props.getRag().setMinScore(0.5);
        props.getRag().setMaxHistoryScore(0.98);
        props.getRag().setTopKHistory(5);
        props.getRag().setTopKTemplate(5);
        props.getRag().setMinQuality(RagQuality.VERIFIED);
        return props;
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

    /** [语料治理] 带质量等级的行（用于分级过滤用例）。 */
    private static RagVector rowWithQuality(String sourceType, int refId, String embedText, String embedding,
                                            String quality) {
        RagVector row = row(sourceType, refId, embedText, embedding);
        row.setQuality(quality);
        return row;
    }
}
