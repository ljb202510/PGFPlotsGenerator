package com.pg.pgfplots.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.entity.GenerationHistory;
import com.pg.pgfplots.entity.RagVector;
import com.pg.pgfplots.mapper.GenerationHistoryMapper;
import com.pg.pgfplots.service.rag.EmbeddingClient;
import com.pg.pgfplots.service.rag.RagQuality;
import com.pg.pgfplots.service.rag.RagUnavailableException;
import com.pg.pgfplots.service.rag.Retriever;
import com.pg.pgfplots.service.rag.VectorStore;
import com.pg.pgfplots.util.ChartCodeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [RAG] CLI 工具（批次1/A1；[语料治理] 扩展 purge 报告与 verify 离线定级）。
 * <p>正常 Web 启动（无 --rag-cli 参数）时本类 no-op；CLI 执行完立即退出。</p>
 *
 * <p>支持模式：</p>
 * <ul>
 *   <li>{@code seed} —— 重建模板库（幂等），并标记 {@code quality='golden'}</li>
 *   <li>{@code backfill} —— 回填历史成功案例（新入库一律 {@code unverified}）</li>
 *   <li>{@code purge} —— <b>只读报告</b>脏语料分类与条数，<b>不删除任何数据</b></li>
 *   <li>{@code purge:apply} —— 显式确认后才真正删除（T1 内容非 tikz + T5 重复旧副本）</li>
 *   <li>{@code verify:<userId>} —— 离线定级：把「编译成功 且 静态零违例」的案例提升为 {@code verified}；
 *       同一 {@code embed_text} 只保留最新一条（避免重复副本占满召回名额）</li>
 *   <li>{@code demo:查询文本[:userId]} —— 召回演示</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagCli implements CommandLineRunner {

    private static final int BACKFILL_LIMIT = 2000;
    private static final int BACKFILL_BATCH = 20;
    /** [v1.2] purge / verify 单次扫描的历史向量上限 */
    private static final int SCAN_LIMIT = 5000;
    private static final long BACKFILL_BATCH_SLEEP_MS = 200;

    /** [语料治理] data_source 取值：只标「有无上传数据集」 */
    private static final String DS_DATASET = "dataset";
    private static final String DS_NO_DATASET = "no-dataset";

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
                purge(false);
            } else if ("purge:apply".equals(mode)) {
                purge(true);
            } else if (mode.startsWith("verify:")) {
                verify(parseUserId(mode.substring("verify:".length()), 1));
            } else if (mode.startsWith("demo:")) {
                demo(mode.substring("demo:".length()));
            } else {
                System.out.println("未知模式：" + mode
                        + "（支持 seed / backfill / purge / purge:apply / verify:<userId> / demo:查询文本[:userId]）");
            }
        } catch (RagUnavailableException e) {
            System.err.println(e.getMessage());
            System.err.println("提示：请确认已配置 EMBEDDING_API_KEY / EMBEDDING_API_URL / EMBEDDING_MODEL（spring-backend/.env 或环境变量）。");
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

    /** 解析 {@code verify:<userId>} 的 userId，非法或缺失时回落默认值。 */
    private int parseUserId(String raw, int fallback) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty() || !s.chars().allMatch(Character::isDigit)) {
            return fallback;
        }
        return Integer.parseInt(s);
    }

    /** seed：重建模板库（幂等，重复执行行数不变）；模板一律标为人工定义真值 golden */
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
        System.out.println("[RAG] 模板库已重建，共 " + rows.size() + " 条（quality=" + RagQuality.GOLDEN + "）");
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
        // [语料治理] 模板是人工撰写的图型范例 → 人工定义真值，恒不参与等级过滤
        row.setQuality(RagQuality.GOLDEN);
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
            // [语料治理] 与 indexHistoryAsync 同一套准入标准（ChartCodeValidator.violationsForIngest）
            List<String> violations = ChartCodeValidator.violationsForIngest(h.getGenerationCode());
            if (!violations.isEmpty()) {
                skip++;
                System.out.println("  [跳过] historyId=" + h.getHistoryId() + "：命中 " + String.join(",", violations));
            } else {
                try {
                    String desc = h.getGenerationDescription() == null || h.getGenerationDescription().isBlank()
                            ? "AI图表生成" : h.getGenerationDescription();
                    String content = "需求：" + desc + "\n代码：\n" + h.getGenerationCode();
                    String title = desc.length() > 50 ? desc.substring(0, 50) + "..." : desc;
                    String dataSource = h.getDataId() == null ? DS_NO_DATASET : DS_DATASET;
                    float[] vec = embeddingClient.embed(desc);
                    // [语料治理] 回填同样一律 unverified，由 verify 离线定级提升
                    vectorStore.upsertHistory(h.getUserId(), h.getHistoryId(), title, desc, content, vec,
                            model, dim, RagQuality.UNVERIFIED, dataSource);
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
     * purge：脏语料分类报告（默认<b>只读</b>），可选显式删除。
     *
     * <p>[语料治理] 默认不删除任何数据：未定级案例（{@code unverified}）天然不参与召回，等价于已下架，
     * 但保留可回溯、完全可逆。只有显式传 {@code purge:apply} 才真正删除。</p>
     *
     * <p>分类判据：</p>
     * <ul>
     *   <li>T1 —— {@code content} 不含 tikz 画布（内容根本不是代码，如闲聊/乱输入被兜底提取）</li>
     *   <li>T5 —— 同一 {@code embed_text} 的重复旧副本（只保留 {@code vector_id} 最大的一条）</li>
     * </ul>
     *
     * @param apply true 才真正执行删除
     */
    private void purge(boolean apply) {
        List<RagVector> rows = vectorStore.loadAllHistory(SCAN_LIMIT);
        System.out.println("[RAG] 扫描历史向量：" + rows.size() + " 条");

        List<RagVector> noTikz = new ArrayList<>();
        List<RagVector> duplicates = new ArrayList<>();
        Map<String, RagVector> newestByText = new HashMap<>();
        for (RagVector row : rows) {
            String content = row.getContent() == null ? "" : row.getContent();
            if (!ChartCodeValidator.hasTikzStructure(content)) {
                noTikz.add(row);
                continue;   // T1 已归类，不再参与重复统计
            }
            String key = row.getEmbedText() == null ? "" : row.getEmbedText().trim();
            RagVector current = newestByText.get(key);
            if (current == null) {
                newestByText.put(key, row);
            } else if (row.getVectorId() > current.getVectorId()) {
                duplicates.add(current);
                newestByText.put(key, row);
            } else {
                duplicates.add(row);
            }
        }

        System.out.println("  [分类] T1 内容非 tikz 代码：" + noTikz.size() + " 条");
        System.out.println("  [分类] T5 同一需求的历史重复旧副本：" + duplicates.size() + " 条");
        System.out.println("  合计可清理 " + (noTikz.size() + duplicates.size()) + " 条 / 扫描总数 " + rows.size() + " 条");

        if (!apply) {
            System.out.println("[RAG] 本次为【只读报告】，未删除任何数据。");
            System.out.println("      说明：未定级案例天然不参与召回（等价于已下架），删与不删对召回结果无差别；");
            System.out.println("      如需真正删除以精简存储，请显式执行：--rag-cli=purge:apply");
            return;
        }

        int removed = 0;
        for (RagVector row : noTikz) {
            vectorStore.deleteHistoryRef(row.getRefId());
            removed++;
        }
        for (RagVector row : duplicates) {
            vectorStore.deleteHistoryRef(row.getRefId());
            removed++;
        }
        System.out.println("[RAG] 已删除 " + removed + " 条，剩余约 " + (rows.size() - removed) + " 条");
    }

    /**
     * [语料治理] verify：离线定级（纯读取 + 更新，<b>不调用任何大模型</b>）。
     *
     * <p>判定者：① 编译成功（{@code generation_history.generation_path} 非空——该字段只在编译成功时写入）
     * ② 静态零违例（{@link ChartCodeValidator#violationsForIngest}）。
     * 两者都<b>独立于模型自评</b>，是当前可自动获得的最强判据。</p>
     *
     * <p>幂等：每次先把该账号的历史向量全部重置为 {@code unverified}，再重新判定，
     * 因此可重复执行、可随时回退。</p>
     *
     * <p>去重：同一 {@code embed_text} 只保留 {@code vector_id} 最大（最新）的一条为 {@code verified}，
     * 其余保持 {@code unverified}——避免同一需求的多个副本占满召回名额（历史 top-k 只有 3）。</p>
     *
     * @param userId 目标账号；不传或非法时默认 1
     */
    private void verify(int userId) {
        System.out.println("[RAG] 离线定级：userId=" + userId + "（纯读取 + 更新，不调用大模型）");
        List<RagVector> rows = new ArrayList<>();
        for (RagVector row : vectorStore.loadAllHistory(SCAN_LIMIT)) {
            if (row.getUserId() != null && row.getUserId() == userId) {
                rows.add(row);
            }
        }
        System.out.println("  该账号历史向量：" + rows.size() + " 条");
        if (rows.isEmpty()) {
            System.out.println("[RAG] 该账号暂无历史向量，无需定级。");
            return;
        }

        // 该账号全部生成历史：用于判定「编译成功」与「有无数据集」
        Map<Integer, GenerationHistory> historyById = new HashMap<>();
        for (GenerationHistory h : historyMapper.selectList(new LambdaQueryWrapper<GenerationHistory>()
                .eq(GenerationHistory::getUserId, userId))) {
            historyById.put(h.getHistoryId(), h);
        }

        // 1) 重置为 unverified（幂等）
        List<Integer> allRefIds = new ArrayList<>(rows.size());
        for (RagVector row : rows) {
            allRefIds.add(row.getRefId());
        }
        vectorStore.markHistoryQuality(allRefIds, RagQuality.UNVERIFIED);

        // 2) 逐条判定资格
        List<RagVector> qualified = new ArrayList<>();
        int noCompile = 0, violated = 0, orphan = 0;
        for (RagVector row : rows) {
            GenerationHistory h = historyById.get(row.getRefId());
            if (h == null) {
                orphan++;
                continue;
            }
            String path = h.getGenerationPath();
            if (path == null || path.isBlank()) {
                noCompile++;    // 从未编译成功
                continue;
            }
            List<String> hits = ChartCodeValidator.violationsForIngest(row.getContent());
            if (!hits.isEmpty()) {
                violated++;
                continue;
            }
            qualified.add(row);
        }

        // 3) 同一 embed_text 只保留最新一条
        Map<String, RagVector> newestByText = new HashMap<>();
        for (RagVector row : qualified) {
            String key = row.getEmbedText() == null ? "" : row.getEmbedText().trim();
            RagVector current = newestByText.get(key);
            if (current == null || row.getVectorId() > current.getVectorId()) {
                newestByText.put(key, row);
            }
        }
        List<Integer> keepIds = new ArrayList<>(newestByText.size());
        for (RagVector row : newestByText.values()) {
            keepIds.add(row.getRefId());
        }
        int updated = vectorStore.markHistoryQuality(keepIds, RagQuality.VERIFIED);

        // 4) 数据来源标记（只标有无数据集）
        List<Integer> withDataset = new ArrayList<>();
        List<Integer> noDataset = new ArrayList<>();
        for (Integer refId : keepIds) {
            GenerationHistory h = historyById.get(refId);
            if (h != null && h.getDataId() != null) {
                withDataset.add(refId);
            } else {
                noDataset.add(refId);
            }
        }
        vectorStore.markHistoryDataSource(withDataset, DS_DATASET);
        vectorStore.markHistoryDataSource(noDataset, DS_NO_DATASET);

        // 5) 报告
        System.out.println("  [跳过] 从未编译成功：" + noCompile + " 条");
        System.out.println("  [跳过] 命中准入判据：" + violated + " 条");
        System.out.println("  [跳过] 找不到对应生成历史：" + orphan + " 条");
        System.out.println("  [去重] 合格候选 " + qualified.size() + " 条 → 保留最新 " + keepIds.size()
                + " 条（同一 embed_text 只留 1 条）");
        System.out.println("[RAG] 定级完成：verified " + updated + " 条；其余保持 "
                + RagQuality.UNVERIFIED + "（不参与召回）");
        System.out.println("  数据来源：dataset " + withDataset.size() + " 条 / no-dataset " + noDataset.size()
                + " 条（no-dataset 含「公开数据」与「模型自拟示意数据」，无法自动区分）");
        System.out.println("  阈值参考：当前 app.rag.min-quality=" + appProperties.getRag().getMinQuality());
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
        System.out.println("[RAG] 查询：" + query + "（userId=" + userId
                + "，min-quality=" + appProperties.getRag().getMinQuality() + "）");
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
