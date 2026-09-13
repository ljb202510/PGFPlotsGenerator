package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.service.AdminLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理员-系统监控：/api/admin/log/*。
 */
@RestController
@RequestMapping("/api/admin/log")
@RequiredArgsConstructor
public class AdminLogController {

    private final AdminLogService adminLogService;

    /** API 调用统计 */
    @GetMapping("/api-stats")
    public Result<Map<String, Object>> apiStats(@RequestParam(value = "startDate", required = false) String startDate,
                                                @RequestParam(value = "endDate", required = false) String endDate) {
        return Result.ok(adminLogService.apiStats(startDate, endDate));
    }

    /** 系统日志 */
    @GetMapping("/system-logs")
    public Result<Map<String, Object>> systemLogs(@RequestParam(value = "page", required = false) String page,
                                                  @RequestParam(value = "pageSize", required = false) String pageSize,
                                                  @RequestParam(value = "status", required = false) String status,
                                                  @RequestParam(value = "startDate", required = false) String startDate,
                                                  @RequestParam(value = "endDate", required = false) String endDate,
                                                  @RequestParam(value = "search", required = false) String search) {
        return Result.ok(adminLogService.systemLogs(page, pageSize, status, startDate, endDate, search));
    }

    /** 系统健康概览 */
    @GetMapping("/health-overview")
    public Result<Map<String, Object>> healthOverview() {
        return Result.ok(adminLogService.healthOverview());
    }

    /** 添加测试日志 */
    @PostMapping("/add-test-log")
    public Result<Map<String, Object>> addTestLog(@RequestBody(required = false) Map<String, String> body) {
        Map<String, String> params = body == null ? Map.of() : body;
        Integer logId = adminLogService.addTestLog(params.get("message"), params.get("status"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("log_id", logId);
        return Result.ok("测试日志添加成功", data);
    }

    /** 数据库表状态 */
    @GetMapping("/tables-status")
    public Result<Map<String, Object>> tablesStatus() {
        return Result.ok(adminLogService.tablesStatus());
    }
}
