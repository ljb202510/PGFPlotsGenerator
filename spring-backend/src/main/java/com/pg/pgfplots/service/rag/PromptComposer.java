package com.pg.pgfplots.service.rag;

import java.util.List;

/**
 * [RAG] few-shot 段落组装。
 * <p>关键措辞约束：显式声明「仅参考图型写法，禁止照搬数据」，并说明与 SYSTEM_PROMPT
 * 中【上下文独立指令】不冲突——示例不属于对话历史，不会污染上下文独立语义。
 * 召回为空返回 ""，调用方拼接结果与旧版逐字符一致（零破坏）。</p>
 */
public final class PromptComposer {

    private PromptComposer() {
    }

    public static String compose(List<Retriever.Retrieved> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n【参考示例（仅供图型写法参考）】");
        sb.append("\n以下是从过往成功案例与图型模板库中检索到的相似需求示例。注意：这些示例与【上下文独立指令】不冲突——");
        sb.append("它们不是本次对话历史，仅用于参考其 PGFPlots 图型选择与语法写法；禁止照搬示例中的任何数据、数值、坐标与标签，");
        sb.append("当前结果仍只依据本次用户消息与数据文件。");
        int i = 1;
        for (Retriever.Retrieved hit : hits) {
            sb.append("\n\n--- 示例").append(i++).append("（").append(hit.title()).append("，相似度 ")
              .append(String.format("%.2f", hit.score())).append("）---\n");
            sb.append(hit.content());
        }
        return sb.toString();
    }
}
