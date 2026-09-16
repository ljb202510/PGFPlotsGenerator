package com.pg.pgfplots.service.rag;

/**
 * [语料治理] RAG 语料质量等级常量与「可召回」判定。
 *
 * <h3>为什么要分级</h3>
 * <p>原实现「生成成功即入库」，没有任何质量判定——既不校验编译成功，也不校验语义正确。
 * 用模型自己的输出教模型是<b>闭环自举</b>：输出正确时强化正确，输出错误时<b>同样强化错误</b>。
 * 因此引入分层，把「<b>入库</b>」与「<b>可召回</b>」解耦：
 * 任何案例先入库，只有被<b>独立于生成者的判据</b>判定过的才参与召回。</p>
 *
 * <h3>等级与判定者</h3>
 * <ul>
 *   <li>{@link #GOLDEN} —— 人工定义真值（模板分区；或经人工审阅确认的范例）。判定者：人。</li>
 *   <li>{@link #VERIFIED} —— 编译成功 且 静态零违例，由 {@code --rag-cli=verify:<userId>} 离线定级。
 *       判定者：编译结果 + 静态规则（两者都独立于模型自评）。</li>
 *   <li>{@link #UNVERIFIED} —— 默认等级，<b>不参与召回</b>（等价于「已下架」但不删数据，完全可逆）。</li>
 * </ul>
 *
 * <h3>已知盲区（务必如实理解，不要高估本机制）</h3>
 * <ul>
 *   <li>本机制只能判定「编译得了」与「结构不违例」，<b>判不了语义与视觉是否正确</b>
 *       （负值柱被挤出可视区、标签重叠、数据编造等都可能编译成功）。</li>
 *   <li>项目<b>没有</b>「用户对单次生成满意/不满意」的信号（{@code feedback} 表是通用反馈，
 *       无 history 关联、无评分），因此没有用户级真值可依赖。</li>
 * </ul>
 *
 * <p>纯静态、无 Spring 依赖，便于 CLI 与单元测试复用，并避免等级字符串在多处拼写漂移。</p>
 */
public final class RagQuality {

    /** 人工定义真值（模板分区；或经人工审阅确认的范例）。最高权重。 */
    public static final String GOLDEN = "golden";

    /** 编译成功 且 静态零违例。 */
    public static final String VERIFIED = "verified";

    /** 默认等级：不参与召回。 */
    public static final String UNVERIFIED = "unverified";

    private RagQuality() {
    }

    /**
     * 是否达到可召回门槛。
     *
     * <p>等级有序：{@code golden > verified > unverified}。历史分区无 {@code golden}，模板分区全为 {@code golden}。</p>
     *
     * @param quality    该条语料的等级（null / 未知值一律按最低级处理，保守优先）
     * @param minQuality 门槛（{@code unverified} 表示不过滤，等价于旧行为）
     */
    public static boolean recallable(String quality, String minQuality) {
        return rank(quality) >= rank(minQuality);
    }

    /** 等级序号：golden=2 / verified=1 / unverified(含 null 与未知值)=0。 */
    private static int rank(String quality) {
        if (GOLDEN.equals(quality)) {
            return 2;
        }
        if (VERIFIED.equals(quality)) {
            return 1;
        }
        return 0;
    }
}
