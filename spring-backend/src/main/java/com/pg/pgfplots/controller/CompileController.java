package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.security.SecurityUtils;
import com.pg.pgfplots.service.CompileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

/**
 * LaTeX 编译接口：/api/compile/*。
 */
@RestController
@RequestMapping("/api/compile")
@RequiredArgsConstructor
public class CompileController {

    private final CompileService compileService;

    /** 编译指定历史记录 */
    @PostMapping("/{history_id}")
    public Result<Map<String, Object>> compile(@PathVariable("history_id") String historyId) {
        return Result.ok("编译成功", compileService.compile(SecurityUtils.userId(), historyId));
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
