package com.pg.pgfplots.service;

import com.pg.pgfplots.entity.SystemLog;
import com.pg.pgfplots.mapper.AdminMapper;
import com.pg.pgfplots.mapper.SystemLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 管理员-系统监控，对应 Node 的 {@code routes/AdminLog.js}。
 */
@Service
@RequiredArgsConstructor
public class AdminLogService {

    private final AdminMapper adminMapper;
    private final SystemLogMapper systemLogMapper;

    /** API 调用统计。 */
    public Map<String, Object> apiStats(String startDate, String endDate) {
        List<Map<String, Object>> logs = adminMapper.selectApiLogs(startDate, endDate);

        int total = logs.size();
        int success = 0;
        for (Map<String, Object> log : logs) {
            if ("success".equals(log.get("call_status"))) {
                success++;
            }
        }
        int failed = total - success;
        double successRate = total > 0 ? round2(success * 100.0 / total) : 0;

        // 时间序列按日期分组（升序）
        Map<String, Map<String, Object>> grouped = new TreeMap<>();
        for (Map<String, Object> log : logs) {
            String date = extractDate(log.get("call_time"));
            if (date.isEmpty()) {
                continue;
            }
            Map<String, Object> bucket = grouped.computeIfAbsent(date, key -> {
                Map<String, Object> inner = new LinkedHashMap<>();
                inner.put("date", key);
                inner.put("total", 0);
                inner.put("success", 0);
                inner.put("failed", 0);
                return inner;
            });
            bucket.put("total", ((Number) bucket.get("total")).intValue() + 1);
            if ("success".equals(log.get("call_status"))) {
                bucket.put("success", ((Number) bucket.get("success")).intValue() + 1);
            } else {
                bucket.put("failed", ((Number) bucket.get("failed")).intValue() + 1);
            }
        }
        List<Map<String, Object>> timeSeries = new ArrayList<>(grouped.values());

        // 最近失败调用（最多 10 条）
        List<Map<String, Object>> recentFailures = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            if (recentFailures.size() >= 10) {
                break;
            }
            if (!"failed".equals(log.get("call_status"))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("call_id", log.get("call_id"));
            item.put("user_id", log.get("user_id"));
            item.put("username", log.get("username") == null ? "Unknown" : log.get("username"));
            item.put("call_status", log.get("call_status"));
            item.put("call_time", log.get("call_time"));
            item.put("call_error", log.get("call_error"));
            item.put("generation_description",
                    log.get("generation_description") == null ? "No description" : log.get("generation_description"));
            recentFailures.add(item);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_calls", total);
        summary.put("success_calls", success);
        summary.put("failed_calls", failed);
        summary.put("success_rate", successRate);

        Map<String, Object> responseTime = new LinkedHashMap<>();
        responseTime.put("avg", 245);
        responseTime.put("p95", 420);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summary);
        data.put("timeSeries", timeSeries);
        data.put("recentFailures", recentFailures);
        data.put("responseTime", responseTime);
        return data;
    }

    /** 系统日志（分页 + 筛选 + 近 7 天统计）。 */
    public Map<String, Object> systemLogs(String pageRaw, String pageSizeRaw, String status,
                                          String startDate, String endDate, String search) {
        int page = Math.max(1, parseOrDefault(pageRaw, 1));
        int pageSize = Math.max(1, parseOrDefault(pageSizeRaw, 20));
        int offset = (page - 1) * pageSize;

        List<Map<String, Object>> logs =
                adminMapper.selectSystemLogs(status, search, startDate, endDate, pageSize, offset);
        long total = adminMapper.countSystemLogs(status, search, startDate, endDate);

        List<Map<String, Object>> logsWithType = new ArrayList<>(logs.size());
        for (Map<String, Object> log : logs) {
            Map<String, Object> item = new LinkedHashMap<>(log);
            Object sysStatus = log.get("system_status");
            String type = "error".equals(sysStatus) ? "danger"
                    : ("warning".equals(sysStatus) ? "warning" : "success");
            item.put("status_type", type);
            logsWithType.add(item);
        }

        String sevenDaysAgo = LocalDate.now().minusDays(7).toString();
        List<Map<String, Object>> stats = adminMapper.selectSystemLogStats(sevenDaysAgo);

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", page);
        pagination.put("pageSize", pageSize);
        pagination.put("total", total);
        pagination.put("totalPages", (long) Math.ceil((double) total / pageSize));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logs", logsWithType);
        data.put("pagination", pagination);
        data.put("stats", stats);
        return data;
    }

    /** 系统健康概览。 */
    public Map<String, Object> healthOverview() {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<Map<String, Object>> recentApi = adminMapper.selectRecentApiStats(oneHourAgo);
        long recentErrors = adminMapper.countRecentErrors(oneHourAgo);

        LocalDateTime dayAgo = LocalDateTime.now().minusHours(24);
        long activeUsers = adminMapper.countActiveUsers(dayAgo);

        Map<String, Object> fileStats = adminMapper.selectRecentFileStats(dayAgo);
        long totalFiles = fileStats == null ? 0 : toLong(fileStats.get("file_count"));
        long totalSize = fileStats == null ? 0 : toLong(fileStats.get("total_size"));
        double storageUsed = round2(totalSize / 1048576.0);

        String systemStatus = "healthy";
        String statusColor = "success";
        if (recentErrors > 10) {
            systemStatus = "critical";
            statusColor = "danger";
        } else if (recentErrors > 3) {
            systemStatus = "warning";
            statusColor = "warning";
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("recentApi", recentApi);
        metrics.put("recentErrors", recentErrors);
        metrics.put("activeUsers", activeUsers);
        metrics.put("newFiles", totalFiles);
        metrics.put("storageUsed", storageUsed);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("systemStatus", systemStatus);
        data.put("statusColor", statusColor);
        data.put("metrics", metrics);
        return data;
    }

    /** 添加测试日志。 */
    public Integer addTestLog(String message, String status) {
        SystemLog log = new SystemLog();
        log.setSystemStatus(status == null || status.isEmpty() ? "normal" : status);
        log.setLogTime(LocalDateTime.now());
        log.setError(message == null || message.isEmpty() ? "测试日志" : message);
        systemLogMapper.insert(log);
        return log.getSysId();
    }

    /** 数据库表状态。 */
    public Map<String, Object> tablesStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users", adminMapper.countUsers());
        data.put("api_log", adminMapper.countApiLog());
        data.put("system_log", adminMapper.countSystemLog());
        data.put("generation_history", adminMapper.countGenerationHistory());
        data.put("data_file", adminMapper.countDataFile());
        return data;
    }

    private String extractDate(Object callTime) {
        if (callTime == null) {
            return "";
        }
        String value = callTime.toString();
        int tIndex = value.indexOf('T');
        if (tIndex >= 0) {
            return value.substring(0, tIndex);
        }
        int spaceIndex = value.indexOf(' ');
        if (spaceIndex >= 0) {
            return value.substring(0, spaceIndex);
        }
        return value.length() >= 10 ? value.substring(0, 10) : value;
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private int parseOrDefault(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
