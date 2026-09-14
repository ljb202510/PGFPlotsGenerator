package com.pg.pgfplots.service;

import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.common.ErrorTypes;
import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.entity.ApiLog;
import com.pg.pgfplots.mapper.ApiLogMapper;
import com.pg.pgfplots.util.SystemLogWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * [G1] 编译异步任务服务（批次2）：内存任务表 + 独立线程池。
 * <p>POST 提交立即返回 task_id；GET 轮询终态。已知取舍：重启丢失任务状态
 * （{@code compile_task} 表持久化属演进版，不在本批）。</p>
 * <p>[G2] 工作线程内 {@link Semaphore} 限流 XeLaTeX 进程并发（permits = {@code app.latex.max-concurrency}）。</p>
 * <p>注意：不使用 {@code @Async}——同类内自调用会让异步代理失效，这里直接向注入的线程池 submit。</p>
 */
@Slf4j
@Service
public class CompileTaskService {

    /** status: queued | running | success | failed（后两者为终态） */
    public record CompileTask(String taskId, Integer userId, Integer historyId, String status,
                              String pdfPath, Long fileSize, String error, Long durationMs,
                              LocalDateTime createdAt) {
    }

    /** 内存任务表上限，超出后淘汰最旧的终态任务 */
    private static final int MAX_TASKS = 500;

    private final Map<String, CompileTask> tasks = new ConcurrentHashMap<>();
    private final Semaphore xelatexPermits;
    private final CompileService compileService;
    private final SystemLogWriter systemLogWriter;
    private final ThreadPoolTaskExecutor compileExecutor;
    private final ApiLogMapper apiLogMapper;

    public CompileTaskService(CompileService compileService, AppProperties appProperties,
                              SystemLogWriter systemLogWriter,
                              ApiLogMapper apiLogMapper,
                              @Qualifier("compileTaskExecutor") ThreadPoolTaskExecutor compileExecutor) {
        this.compileService = compileService;
        this.systemLogWriter = systemLogWriter;
        this.apiLogMapper = apiLogMapper;
        this.compileExecutor = compileExecutor;
        this.xelatexPermits = new Semaphore(Math.max(1, appProperties.getLatex().getMaxConcurrency()));
    }

    /** 提交任务：入队后立即返回 queued 态任务；队列满则标记 failed 并抛 503（不静默丢弃）。 */
    public CompileTask submit(Integer userId, Integer historyId) {
        if (tasks.size() >= MAX_TASKS) {
            evictOldestFinished();
        }
        String taskId = "ct_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 100000);
        CompileTask task = new CompileTask(taskId, userId, historyId, "queued",
                null, null, null, null, LocalDateTime.now());
        tasks.put(taskId, task);
        try {
            compileExecutor.submit(() -> run(taskId, userId, historyId));
        } catch (RejectedExecutionException e) {
            update(taskId, "failed", null, null, "编译任务队列已满", null);
            systemLogWriter.warning("[COMPILE] 任务队列已满，taskId=" + taskId + " 已标记失败");
            recordCompileFailure(userId, historyId, ErrorTypes.COMPILE_QUEUE_FULL, "编译任务队列已满", null);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "编译任务队列已满，请稍后重试");
        }
        log.info("[COMPILE] 任务已提交 taskId={} historyId={} user={}", taskId, historyId, userId);
        return task;
    }

    /** 查询任务：归属校验防越权；不存在（含重启丢失）返回 null，由 Controller 转 404。 */
    public CompileTask get(String taskId, Integer userId) {
        CompileTask task = tasks.get(taskId);
        return (task != null && task.userId().equals(userId)) ? task : null;
    }

    /** 工作线程主体：[G2] 先取 Semaphore 许可再编译，耗时全程计时（含排队等待）。 */
    private void run(String taskId, Integer userId, Integer historyId) {
        long start = System.nanoTime();
        update(taskId, "running", null, null, null, null);
        try {
            xelatexPermits.acquire();
            // [G2] 观测点：拿到许可即真正进入 XeLaTeX 编译，可用日志时间戳核对并发上限
            log.info("[COMPILE] 获得编译许可，开始编译 taskId={} 剩余许可={}", taskId, xelatexPermits.availablePermits());
            try {
                Map<String, Object> result = compileService.compile(userId, String.valueOf(historyId));
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                Object size = result.get("file_size");
                update(taskId, "success", (String) result.get("pdf_path"),
                        size instanceof Number n ? n.longValue() : null, null, durationMs);
                log.info("[COMPILE] 任务成功 taskId={} durationMs={}", taskId, durationMs);
            } finally {
                xelatexPermits.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            update(taskId, "failed", null, null, "编译任务被中断", null);
            recordCompileFailure(userId, historyId, ErrorTypes.COMPILE_ERROR, "编译任务被中断", null);
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String msg = e.getMessage() == null ? "编译失败" : e.getMessage();
            update(taskId, "failed", null, null,
                    msg.length() > 500 ? msg.substring(0, 500) : msg, durationMs);
            log.warn("[COMPILE] 任务失败 taskId={} durationMs={}：{}", taskId, durationMs, msg);
            systemLogWriter.error("[COMPILE] 编译任务失败 taskId=" + taskId + "：" + msg);
            recordCompileFailure(userId, historyId, ErrorTypes.COMPILE_ERROR, msg, durationMs);
        }
    }

    /**
     * [E2] 编译类失败事件落库（api_log 语义扩展为「AI 链路失败事件日志」）。
     * <p>本方法是全仓唯一的编译类失败落库点——{@code CompileTaskService.run} 是
     * {@code CompileService.compile} 的唯一调用点，禁止在 CompileService 内重复落库，否则一次失败计两条。</p>
     * <p>不写 prompt_version：编译发生在生成之后，此处无法得知当次生成所用版本，写当前版本会造成误读，留 NULL。</p>
     */
    private void recordCompileFailure(Integer userId, Integer historyId, String errorType,
                                      String message, Long durationMs) {
        try {
            String msg = message == null ? "编译失败" : message;
            ApiLog apiLog = new ApiLog();
            apiLog.setUserId(userId);
            apiLog.setHistoryId(historyId);
            apiLog.setCallStatus("failed");
            apiLog.setCallTime(LocalDateTime.now());
            apiLog.setErrorType(errorType);
            apiLog.setDurationMs(durationMs);
            apiLog.setCallError(msg.length() > 500 ? msg.substring(0, 500) : msg);
            apiLogMapper.insert(apiLog);
        } catch (Exception e) {
            log.error("[COMPILE] 失败事件落库出错: {}", e.getMessage());
        }
    }

    private synchronized void update(String taskId, String status, String pdfPath,
                                     Long fileSize, String error, Long durationMs) {
        CompileTask cur = tasks.get(taskId);
        if (cur == null) {
            return;
        }
        tasks.put(taskId, new CompileTask(cur.taskId(), cur.userId(), cur.historyId(), status,
                pdfPath != null ? pdfPath : cur.pdfPath(),
                fileSize != null ? fileSize : cur.fileSize(),
                error != null ? error : cur.error(),
                durationMs != null ? durationMs : cur.durationMs(),
                cur.createdAt()));
    }

    /** 内存表防膨胀：淘汰最旧的终态任务（success/failed），queued/running 不淘汰。 */
    private synchronized void evictOldestFinished() {
        tasks.values().stream()
                .filter(t -> "success".equals(t.status()) || "failed".equals(t.status()))
                .min(Comparator.comparing(CompileTask::createdAt))
                .ifPresent(t -> tasks.remove(t.taskId()));
    }
}
