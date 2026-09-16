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
     *
     * @param dataSource [语料治理] dataset / no-dataset——**只标有无上传数据集**；
     *                   「使用公开统计数据」与「模型自拟示意数据」都不带数据集、无法自动区分，属已知盲区
     */
    @Async("ragIndexExecutor")
    public void indexHistoryAsync(Integer userId, Integer historyId, String description, String chartCode,
                                  String dataSource) {
        try {
            if (!appProperties.getRag().isEnabled() || historyId == null
                    || chartCode == null || chartCode.isBlank()) {
                return;
            }
            if (isBlank(appProperties.getEmbedding().getApiKey())) {
                return;
            }
            // [语料治理] 准入校验：命中任何「明显无效」判据的案例绝不进向量库——
            // 否则下次同题请求会把它当 few-shot 范例照抄，形成自我强化循环
            // （history 315/318/329 重复系列事故、history 27/28/30 闲聊被当代码事故）。
            // 判据集合见 ChartCodeValidator.violationsForIngest：含 NO_TIKZ 与 R1/R2/R3/R5/R6/R8/R9/R12，
            // 刻意排除已被 LatexCompiler.preprocess 兜底的 R7/R10/R11（纳入会误杀有效案例）。
            List<String> violations = ChartCodeValidator.violationsForIngest(chartCode);
            if (!violations.isEmpty()) {
                String hit = String.join(",", violations);
                log.warn("[RAG] 跳过索引：命中准入判据 [{}]，historyId={}", hit, historyId);
                systemLogWriter.warning("[RAG] 跳过索引（命中 " + hit + "）historyId=" + historyId);
                return;
            }
            String embedText = description == null || description.isBlank() ? "AI图表生成" : description;
            String content = "需求：" + embedText + "\n代码：\n" + chartCode;
            // title 用于演示与 few-shot 标注：取需求描述截断
            String title = embedText.length() > 50 ? embedText.substring(0, 50) + "..." : embedText;
            float[] vec = embeddingClient.embed(embedText);
            // [语料治理] 新入库一律 unverified：入库 ≠ 可召回，须由 --rag-cli=verify:<userId> 离线定级提升
            vectorStore.upsertHistory(userId, historyId, title, embedText, content, vec,
                    appProperties.getEmbedding().getModel(), appProperties.getEmbedding().getDim(),
                    RagQuality.UNVERIFIED, dataSource);
            log.info("[RAG] 历史案例已索引（unverified）historyId={} user={} dataSource={}",
                    historyId, userId, dataSource);
        } catch (Exception e) {
            log.warn("[RAG] 索引失败（忽略）：historyId={}，{}", historyId, e.getMessage());
            systemLogWriter.warning("[RAG] 索引失败 historyId=" + historyId + "：" + e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
