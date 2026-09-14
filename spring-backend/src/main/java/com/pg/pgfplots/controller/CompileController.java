package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.security.SecurityUtils;
import com.pg.pgfplots.service.CompileService;
import com.pg.pgfplots.service.CompileTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LaTeX 编译接口：/api/compile/*。
 * <p>批次2/G1：POST 提交改为异步任务（立即返回 task_id），新增 GET /task/{task_id} 轮询终态；
 * PDF 流式下载接口契约不变。</p>
 */
@RestController
@RequestMapping("/api/compile")
@RequiredArgsConstructor
public class CompileController {

    private final CompileService compileService;
    private final CompileTaskService compileTaskService;

    /** [G1] 提交编译任务，立即返回 task_id（不再同步阻塞最长 30s）。 */
    @PostMapping("/{history_id}")
    public Result<Map<String, Object>> compile(@PathVariable("history_id") String historyId) {
        Integer userId = SecurityUtils.userId();
        CompileTaskService.CompileTask task =
                compileTaskService.submit(userId, CompileService.parseHistoryId(historyId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task_id", task.taskId());
        data.put("status", task.status());
        data.put("history_id", task.historyId());
        return Result.ok("编译任务已提交", data);
    }

    /** [G1] 查询编译任务状态：success 含 pdf_path/file_size/duration_ms；failed 含 error。 */
    @GetMapping("/task/{task_id}")
    public Result<Map<String, Object>> task(@PathVariable("task_id") String taskId) {
        Integer userId = SecurityUtils.userId();
        CompileTaskService.CompileTask task = compileTaskService.get(taskId, userId);
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "任务不存在或已丢失（服务重启会清空任务状态，请重新提交编译）");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task_id", task.taskId());
        data.put("history_id", task.historyId());
        data.put("status", task.status());
        data.put("pdf_path", task.pdfPath());
        data.put("file_size", task.fileSize());
        data.put("error", task.error());
        data.put("duration_ms", task.durationMs());
        return Result.ok("查询成功", data);
    }

    /** 归属校验后流式返回 PDF */
    @GetMapping("/{history_id}/pdf")
    public ResponseEntity<Resource> pdf(@PathVariable("history_id") String historyId) {
        Path path = compileService.resolvePdf(SecurityUtils.userId(), historyId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(new FileSystemResource(path));
    }
}
