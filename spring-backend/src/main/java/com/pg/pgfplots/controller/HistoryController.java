package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.dto.history.HistoryRecordVO;
import com.pg.pgfplots.security.SecurityUtils;
import com.pg.pgfplots.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 历史记录接口：/api/history/*。
 */
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    /** 历史列表（分页/搜索/日期过滤） */
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(value = "page", required = false) String page,
                                            @RequestParam(value = "limit", required = false) String limit,
                                            @RequestParam(value = "search", required = false) String search,
                                            @RequestParam(value = "start_date", required = false) String startDate,
                                            @RequestParam(value = "end_date", required = false) String endDate) {
        return Result.ok(historyService.list(SecurityUtils.userId(), page, limit, search, startDate, endDate));
    }

    /** 统计信息 */
    @GetMapping("/stats/summary")
    public Result<Map<String, Object>> stats() {
        return Result.ok(historyService.stats(SecurityUtils.userId()));
    }

    /** 导出 CSV */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv() {
        Integer userId = SecurityUtils.userId();
        byte[] body = historyService.exportCsv(userId).getBytes(StandardCharsets.UTF_8);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(historyService.csvFilename(userId), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    /** 历史详情 */
    @GetMapping("/{id}")
    public Result<HistoryRecordVO> detail(@PathVariable("id") String id) {
        return Result.ok(historyService.detail(SecurityUtils.userId(), id));
    }

    /** 删除历史记录 */
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable("id") String id) {
        Integer userId = SecurityUtils.userId();
        historyService.delete(userId, id);
        return Result.ok("历史记录已删除", Map.of("deleted_id", Integer.valueOf(id)));
    }
}
