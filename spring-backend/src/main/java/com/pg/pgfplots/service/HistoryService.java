package com.pg.pgfplots.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.dto.history.HistoryRecordVO;
import com.pg.pgfplots.entity.ApiLog;
import com.pg.pgfplots.entity.GenerationHistory;
import com.pg.pgfplots.mapper.ApiLogMapper;
import com.pg.pgfplots.mapper.GenerationHistoryMapper;
import com.pg.pgfplots.util.TimeFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史记录服务，对应 Node 的 {@code routes/history.js}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final GenerationHistoryMapper historyMapper;
    private final ApiLogMapper apiLogMapper;

    /** 列表（分页 + 搜索 + 可选日期过滤）。 */
    public Map<String, Object> list(Integer userId, String pageRaw, String limitRaw, String search,
                                    String startDate, String endDate) {
        int page = parsePageParam(pageRaw, 1);
        int limit = parsePageParam(limitRaw, 20);
        if (page < 1 || limit < 1) {
            throw BusinessException.badRequest("分页参数无效");
        }
        String searchValue = search == null ? "" : search;

        // 日期过滤在 SQL 中于分页前完成
        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        if (isNotBlank(startDate) && isNotBlank(endDate)) {
            startTime = parseStart(startDate);
            endTime = parseEnd(endDate);
        }

        long total = historyMapper.countHistory(userId, searchValue, startTime, endTime);
        int offset = (page - 1) * limit;
        List<HistoryRecordVO> records = historyMapper.selectHistoryPage(userId, searchValue, startTime, endTime, limit, offset);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("page", page);
        data.put("limit", limit);
        data.put("total_pages", (int) Math.ceil((double) total / limit));
        return data;
    }

    /** 详情。 */
    public HistoryRecordVO detail(Integer userId, String idRaw) {
        Integer id = parseId(idRaw);
        HistoryRecordVO record = historyMapper.selectHistoryDetail(id, userId);
        if (record == null) {
            throw BusinessException.notFound("历史记录不存在");
        }
        return record;
    }

    /** 删除（事务：先删 api_log 再删 generation_history）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer userId, String idRaw) {
        Integer id = parseId(idRaw);
        apiLogMapper.delete(Wrappers.<ApiLog>lambdaQuery()
                .eq(ApiLog::getHistoryId, id)
                .eq(ApiLog::getUserId, userId));
        int affected = historyMapper.delete(Wrappers.<GenerationHistory>lambdaQuery()
                .eq(GenerationHistory::getHistoryId, id)
                .eq(GenerationHistory::getUserId, userId));
        if (affected == 0) {
            throw BusinessException.notFound("历史记录不存在");
        }
        log.info("用户 {} 删除了历史记录 ID: {}", userId, id);
    }

    /** 统计信息。 */
    public Map<String, Object> stats(Integer userId) {
        long total = historyMapper.selectCount(Wrappers.<GenerationHistory>lambdaQuery()
                .eq(GenerationHistory::getUserId, userId));
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long recent = historyMapper.selectCount(Wrappers.<GenerationHistory>lambdaQuery()
                .eq(GenerationHistory::getUserId, userId)
                .ge(GenerationHistory::getGenerationTime, sevenDaysAgo));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_count", total);
        data.put("recent_count", recent);
        data.put("last_7_days", recent);
        return data;
    }

    /** 导出 CSV（最多 1000 条）。 */
    public String exportCsv(Integer userId) {
        List<HistoryRecordVO> records = historyMapper.selectHistoryPage(userId, "", null, null, 1000, 0);
        StringBuilder sb = new StringBuilder();
        sb.append("ID,描述,文件名,生成时间,代码长度\n");
        for (HistoryRecordVO record : records) {
            String description = record.getDescription() == null ? "" : record.getDescription().replace("\"", "\"\"");
            int codeLength = record.getChartCode() == null ? 0 : record.getChartCode().length();
            sb.append(record.getId()).append(',')
                    .append('"').append(description).append('"').append(',')
                    .append(record.getFileName() == null ? "" : record.getFileName()).append(',')
                    .append(TimeFormat.toSecond(record.getCreatedAt())).append(',')
                    .append(codeLength)
                    .append('\n');
        }
        return sb.toString();
    }

    /** CSV 文件名（与原 Node 的 chart_history_{userId}_{timestamp}.csv 一致）。 */
    public String csvFilename(Integer userId) {
        return "chart_history_" + userId + "_" + System.currentTimeMillis() + ".csv";
    }

    private Integer parseId(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (Exception e) {
            throw BusinessException.badRequest("无效的历史记录ID");
        }
    }

    private int parsePageParam(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            throw BusinessException.badRequest("分页参数无效");
        }
    }

    private LocalDateTime parseStart(String date) {
        try {
            return LocalDate.parse(date.trim(), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseEnd(String date) {
        try {
            return LocalDate.parse(date.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
                    .atTime(23, 59, 59, 999_000_000);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isEmpty();
    }
}
