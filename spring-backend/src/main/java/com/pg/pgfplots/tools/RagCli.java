package com.pg.pgfplots.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.entity.GenerationHistory;
import com.pg.pgfplots.entity.RagVector;
import com.pg.pgfplots.mapper.GenerationHistoryMapper;
import com.pg.pgfplots.service.rag.EmbeddingClient;
import com.pg.pgfplots.service.rag.RagUnavailableException;
import com.pg.pgfplots.service.rag.Retriever;
import com.pg.pgfplots.service.rag.VectorStore;
import com.pg.pgfplots.util.ChartCodeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * [RAG] CLI 工具（批次1/A1，配套 scripts/rag_seed.cmd / rag_backfill.cmd / rag_demo.cmd）。
 * 正常 Web 启动（无 --rag-cli 参数）时本类 no-op；CLI 执行完立即退出。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagCli implements CommandLineRunner {

    private static final int BACKFILL_LIMIT = 2000;
    private static final int BACKFILL_BATCH = 20;
    /** [v1.2] purge 单次扫描的历史向量上限 */
    private static final int PURGE_LIMIT = 5000;
    private static final long BACKFILL_BATCH_SLEEP_MS = 200;

    private final EmbeddingClient embeddingClient;
    private final Retriever retriever;
    private final VectorStore vectorStore;
    private final GenerationHistoryMapper historyMapper;
    private final AppProperties appProperties;

    @Override
    public void run(String... args) {
        String mode = parseMode(args);
        if (mode == null) {
            return; // 正常 Web 启动，no-op
        }
        try {
            if ("seed".equals(mode)) {
                seed();
            } else if ("backfill".equals(mode)) {
                backfill();
            } else if ("purge".equals(mode)) {
                purge();
            } else if (mode.startsWith("demo:")) {
                demo(mode.substring("demo:".length()));
            } else {
                System.out.println("未知模式：" + mode + "（支持 seed / backfill / purge / demo:查询文本[:userId]）");
            }
        } catch (RagUnavailableException e) {
            System.err.println(e.getMessage());
            System.err.println("提示：请确认已配置 EMBEDDING_API_KEY / EMBEDDING_API_URL / EMBEDDING_MODEL（../data/.env 或环境变量）。");
        } finally {
            System.exit(0); // CLI 执行完立即退出，不进入 Web 服务
        }
    }

    private String parseMode(String... args) {
        for (String a : args) {
            if (a.startsWith("--rag-cli=")) {
                return a.substring("--rag-cli=".length());
            }
        }
        return null;
    }

    /** seed：重建模板库（幂等，重复执行行数不变） */
    private void seed() {
        System.out.println("[RAG] 开始重建模板库，共 " + RagTemplates.SEEDS.size() + " 条模板...");
        String model = appProperties.getEmbedding().getModel();
        int dim = appProperties.getEmbedding().getDim();
        List<RagVector> rows = new ArrayList<>(RagTemplates.SEEDS.size());
        for (RagTemplates.TemplateSeed seed : RagTemplates.SEEDS) {
            float[] vec = embeddingClient.embed(seed.embedText());
            rows.add(toTemplateRow(seed, vec, model, dim));
            System.out.println("  已向量化：" + seed.title());
        }
        vectorStore.rebuildTemplates(rows);
        System.out.println("[RAG] 模板库已重建，共 " + rows.size() + " 条");
    }

    private RagVector toTemplateRow(RagTemplates.TemplateSeed seed, float[] vec, String model, int dim) {
        RagVector row = new RagVector();
        row.setSourceType("template");
        row.setUserId(null);
        row.setRefId(seed.refId());
        row.setTitle(seed.title());
        row.setEmbedText(seed.embedText());
        row.setContent(seed.content());
        row.setEmbedding(vectorStore.toJson(vec));
        row.setDim(dim);
        row.setModel(model);
        return row;
    }

    /** backfill：批量回填历史成功案例（generation_code 非空），分批 + 限速 + 进度打印 */
    private void backfill() {
        List<GenerationHistory> histories = historyMapper.selectList(new LambdaQueryWrapper<GenerationHistory>()
                .isNotNull(GenerationHistory::getGenerationCode)
                .ne(GenerationHistory::getGenerationCode, "")
                .last("LIMIT " + BACKFILL_LIMIT));
        System.out.println("[RAG] 待回填历史案例：" + histories.size() + " 条");
        String model = appProperties.getEmbedding().getModel();
        int dim = appProperties.getEmbedding().getDim();
        int ok = 0, fail = 0, skip = 0;
        for (int i = 0; i < histories.size(); i++) {
            GenerationHistory h = histories.get(i);
            // [v1.2] 与 indexHistoryAsync 同一套准入标准：多系列坐标重复的图不进向量库
            if (ChartCodeValidator.hasDuplicateSeries(h.getGenerationCode())) {
                skip++;
                System.out.println("  [跳过] historyId=" + h.getHistoryId() + "：多系列坐标重复");
            } else {
                try {
                    String desc = h.getGenerationDescription() == null || h.getGenerationDescription().isBlank()
                            ? "AI图表生成" : h.getGenerationDescription();
                    String content = "需求：" + desc + "\n代码：\n" + h.getGenerationCode();
                    String title = desc.length() > 50 ? desc.substring(0, 50) + "..." : desc;
                    float[] vec = embeddingClient.embed(desc);
                    vectorStore.upsertHistory(h.getUserId(), h.getHistoryId(), title, desc, content, vec, model, dim);
                    ok++;
                } catch (Exception e) {
                    fail++;
                    System.out.println("  [跳过] historyId=" + h.getHistoryId() + "：" + e.getMessage());
                }
            }
            if ((i + 1) % BACKFILL_BATCH == 0) {
                System.out.println("  进度 " + (i + 1) + "/" + histories.size() + "（成功 " + ok + "，失败 " + fail + "）");
                sleepQuietly();
            }
        }
        System.out.println("[RAG] 回填完成：成功 " + ok + "，跳过 " + skip + "，失败 " + fail);
    }

    /**
     * purge：清理已污染的历史向量。
     * <p>用于修复 v1.2 之前写入的错误案例（如两个 {@code \addplot} 坐标完全相同的折线图）——
     * 它们会被当作 few-shot 范例反复喂回模型，形成自我强化循环。入库标准与
     * {@link #backfill()} 及 {@code RagService#indexHistoryAsync} 保持一致。</p>
     */
    private void purge() {
        List<RagVector> rows = vectorStore.loadAllHistory(PURGE_LIMIT);
        System.out.println("[RAG] 待检查历史向量：" + rows.size() + " 条");
        int removed = 0;
        for (RagVector row : rows) {
            if (ChartCodeValidator.hasDuplicateSeries(row.getContent())) {
                vectorStore.deleteHistoryRef(row.getRefId());
                removed++;
                System.out.println("  [清理] refId=" + row.getRefId() + "（" + row.getEmbedText() + "）");
            }
        }
        System.out.println("[RAG] 清理完成：删除 " + removed + " 条，保留 " + (rows.size() - removed) + " 条");
    }

    /** demo：召回演示（面试用），展示模板 + 指定用户历史的 Top-k */
    private void demo(String rest) {
        // 入参格式：查询文本[:userId]，userId 缺省 1
        String query = rest;
        int userId = 1;
        int lastColon = rest.lastIndexOf(':');
        if (lastColon > 0) {
            String tail = rest.substring(lastColon + 1).trim();
            if (!tail.isEmpty() && tail.chars().allMatch(Character::isDigit)) {
                userId = Integer.parseInt(tail);
                query = rest.substring(0, lastColon);
            }
        }
        System.out.println("[RAG] 查询：" + query + "（userId=" + userId + "）");
        float[] queryVec = embeddingClient.embed(query);
        List<Retriever.Retrieved> hits = retriever.retrieve(userId, query, queryVec);
        if (hits.isEmpty()) {
            System.out.println("  无召回结果（低于阈值 " + appProperties.getRag().getMinScore() + " 或库为空）");
            return;
        }
        for (Retriever.Retrieved hit : hits) {
            System.out.printf("  [%s#%s] %s（相似度 %.2f）%n",
                    hit.sourceType(), hit.refId(), hit.title(), hit.score());
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(BACKFILL_BATCH_SLEEP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}


