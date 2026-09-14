package com.pg.pgfplots.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.entity.GenerationHistory;
import com.pg.pgfplots.mapper.GenerationHistoryMapper;
import com.pg.pgfplots.util.LatexCompiler;
import com.pg.pgfplots.util.SystemLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LaTeX 编译服务，对应 Node 的 {@code routes/compile.js}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompileService {

    private final GenerationHistoryMapper historyMapper;
    private final AppProperties appProperties;
    private final SystemLogWriter systemLogWriter;

    /** PDF 归属校验后返回可流式读取的路径。 */
    public Path resolvePdf(Integer userId, String historyIdRaw) {
        Integer historyId = parseHistoryId(historyIdRaw);
        GenerationHistory history = historyMapper.selectOne(Wrappers.<GenerationHistory>lambdaQuery()
                .select(GenerationHistory::getGenerationPath)
                .eq(GenerationHistory::getHistoryId, historyId)
                .eq(GenerationHistory::getUserId, userId));
        if (history == null) {
            throw BusinessException.notFound("历史记录不存在或无权访问");
        }
        String generationPath = history.getGenerationPath();
        if (generationPath == null || generationPath.isEmpty()) {
            throw BusinessException.notFound("该记录尚未生成PDF");
        }

        Path relativeBase = relativeBase();
        Path storageRoot = chartsDir().getParent();
        Path absolute = relativeBase.resolve(generationPath).normalize();
        if (!absolute.startsWith(storageRoot) || !Files.exists(absolute)) {
            throw BusinessException.notFound("PDF文件不存在");
        }
        return absolute;
    }

    /** 编译指定历史记录的代码为 PDF。 */
    public Map<String, Object> compile(Integer userId, String historyIdRaw) {
        Integer historyId = parseHistoryId(historyIdRaw);
        GenerationHistory history = historyMapper.selectOne(Wrappers.<GenerationHistory>lambdaQuery()
                .eq(GenerationHistory::getHistoryId, historyId)
                .eq(GenerationHistory::getUserId, userId));
        if (history == null) {
            throw BusinessException.notFound("历史记录不存在或无权访问");
        }
        String code = history.getGenerationCode();
        if (code == null || code.trim().isEmpty()) {
            throw BusinessException.badRequest("该历史记录没有可编译的图表代码");
        }

        Path userStorageDir = chartsDir().resolve("user" + userId);
        Path tempDir = null;
        try {
            Files.createDirectories(userStorageDir);
            // JDK 原子创建唯一目录：避免同一 userId 在同一毫秒并发编译时共用工作目录，
            // 导致 xelatex 输出互相踩踏、且 safeCleanup 误删其他任务仍在使用的目录
            tempDir = Files.createTempDirectory(userStorageDir, "temp_");

            String processed = LatexCompiler.preprocess(code);
            LatexCompiler.Validation validation =
                    LatexCompiler.validate(processed, appProperties.getLatex().getMaxCodeLength());
            if (!validation.valid()) {
                LatexCompiler.safeCleanup(tempDir);
                throw BusinessException.badRequest(validation.message());
            }

            String document = LatexCompiler.buildDocument(processed);
            Path texFile = tempDir.resolve("hist" + historyId + ".tex");
            Files.writeString(texFile, document, StandardCharsets.UTF_8);

            LatexCompiler.CompileResult result = LatexCompiler.run(texFile, tempDir,
                    appProperties.getLatex().getExecutable(), appProperties.getLatex().getTimeoutMs());

            Path tempPdf = tempDir.resolve("hist" + historyId + ".pdf");
            if (!Files.exists(tempPdf)) {
                saveCompileFailure(historyId, texFile, result.output());
                LatexCompiler.safeCleanup(tempDir);
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, result.output());
            }

            Path finalPdf = userStorageDir.resolve("hist" + historyId + ".pdf");
            Files.move(tempPdf, finalPdf, StandardCopyOption.REPLACE_EXISTING);
            LatexCompiler.safeCleanup(tempDir);
            tempDir = null;

            String relativePath = relativeBase().relativize(finalPdf).toString();
            historyMapper.update(null, Wrappers.<GenerationHistory>lambdaUpdate()
                    .eq(GenerationHistory::getHistoryId, historyId)
                    .eq(GenerationHistory::getUserId, userId)
                    .set(GenerationHistory::getGenerationPath, relativePath));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("history_id", historyId);
            data.put("pdf_path", relativePath);
            data.put("file_size", Files.size(finalPdf));
            return data;
        } catch (BusinessException e) {
            LatexCompiler.safeCleanup(tempDir);
            throw e;
        } catch (Exception e) {
            LatexCompiler.safeCleanup(tempDir);
            log.error("编译错误: {}", e.getMessage());
            systemLogWriter.error("[COMPILE] LaTeX 编译失败: " + e.getMessage());
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /** 保留失败样本到 debug 目录（tex + log），控制台只提示位置。 */
    private void saveCompileFailure(Integer historyId, Path texFile, String output) {
        try {
            if (texFile == null || !Files.exists(texFile)) {
                return;
            }
            Path debugDir = Paths.get(appProperties.getStorage().getDebug()).toAbsolutePath().normalize();
            Files.createDirectories(debugDir);
            String base = "hist" + historyId + "_" + System.currentTimeMillis();
            Path texTarget = debugDir.resolve(base + ".tex");
            Files.copy(texFile, texTarget, StandardCopyOption.REPLACE_EXISTING);
            if (output != null && !output.isEmpty()) {
                Files.writeString(debugDir.resolve(base + ".log"), output, StandardCharsets.UTF_8);
            }
            log.error("[COMPILE] LaTeX 编译失败，失败样本已保存至: {}", texTarget);
        } catch (Exception e) {
            log.error("[COMPILE] 保存编译失败样本出错: {}", e.getMessage());
        }
    }

    /** 解析 history_id，非法即 400（批次2/T4：提升为 public static 供 CompileController 复用）。 */
    public static Integer parseHistoryId(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (Exception e) {
            throw BusinessException.badRequest("无效的历史记录ID");
        }
    }

    private Path chartsDir() {
        return Paths.get(appProperties.getStorage().getCharts()).toAbsolutePath().normalize();
    }

    private Path relativeBase() {
        return Paths.get(appProperties.getStorage().getRelativeBase()).toAbsolutePath().normalize();
    }
}
