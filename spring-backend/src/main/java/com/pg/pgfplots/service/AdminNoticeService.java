package com.pg.pgfplots.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.entity.Notice;
import com.pg.pgfplots.mapper.AdminMapper;
import com.pg.pgfplots.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员-通知管理，对应 Node 的 {@code routes/AdminNotice.js}。
 * <p>管理端仅管理系统广播通知（feedback_id IS NULL），反馈回复通知由反馈模块维护。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminNoticeService {

    private final NoticeMapper noticeMapper;
    private final AdminMapper adminMapper;

    /** 通知列表（分页 + 筛选 + 统计）。 */
    public Map<String, Object> list(String pageRaw, String pageSizeRaw, String keyword,
                                    String startDate, String endDate) {
        int page = Math.max(1, parseOrDefault(pageRaw, 1));
        int pageSize = Math.max(1, parseOrDefault(pageSizeRaw, 10));
        int offset = (page - 1) * pageSize;

        List<Map<String, Object>> notices =
                adminMapper.selectAdminNotices(keyword, startDate, endDate, pageSize, offset);
        long total = adminMapper.countAdminNotices(keyword, startDate, endDate);
        long readCount = adminMapper.countDistinctReadNotices();
        long unreadCount = adminMapper.countUnreadSystemNotices();

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", page);
        pagination.put("pageSize", pageSize);
        pagination.put("total", total);
        pagination.put("totalPages", (long) Math.ceil((double) total / pageSize));

        Map<String, Object> statistics = new LinkedHashMap<>();
        statistics.put("total_count", total);
        statistics.put("read_count", readCount);
        statistics.put("unread_count", unreadCount);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notices", notices);
        data.put("pagination", pagination);
        data.put("statistics", statistics);
        return data;
    }

    /** 通知详情。 */
    public Map<String, Object> detail(String idRaw) {
        Integer id = parseIntOrNull(idRaw);
        Map<String, Object> notice = id == null ? null : adminMapper.selectNoticeDetail(id);
        if (notice == null) {
            throw BusinessException.notFound("通知不存在");
        }
        return notice;
    }

    /** 创建通知。 */
    public Map<String, Object> create(Integer adminId, String title, String content) {
        if (isBlank(title) || isBlank(content)) {
            throw BusinessException.badRequest("标题和内容不能为空");
        }
        Notice notice = new Notice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setAdminId(adminId);
        noticeMapper.insert(notice);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notice_id", notice.getNoticeId());
        return data;
    }

    /** 更新通知（仅系统通知）。 */
    public void update(String idRaw, String title, String content) {
        if (isBlank(title) || isBlank(content)) {
            throw BusinessException.badRequest("标题和内容不能为空");
        }
        Integer id = parseIntOrNull(idRaw);
        Long exists = id == null ? 0 : noticeMapper.selectCount(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getNoticeId, id)
                .isNull(Notice::getFeedbackId));
        if (exists == null || exists == 0) {
            throw BusinessException.notFound("通知不存在");
        }
        noticeMapper.update(null, Wrappers.<Notice>lambdaUpdate()
                .eq(Notice::getNoticeId, id)
                .isNull(Notice::getFeedbackId)
                .set(Notice::getTitle, title)
                .set(Notice::getContent, content));
    }

    /** 删除通知（仅系统通知）。 */
    public void delete(String idRaw) {
        Integer id = parseIntOrNull(idRaw);
        int affected = id == null ? 0 : noticeMapper.delete(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getNoticeId, id)
                .isNull(Notice::getFeedbackId));
        if (affected == 0) {
            throw BusinessException.notFound("通知不存在");
        }
    }

    /** 批量删除（仅系统通知）。 */
    public void batchDelete(List<?> ids) {
        if (ids == null || ids.isEmpty()) {
            throw BusinessException.badRequest("请选择要删除的通知");
        }
        noticeMapper.delete(Wrappers.<Notice>lambdaQuery()
                .in(Notice::getNoticeId, ids)
                .isNull(Notice::getFeedbackId));
    }

    /** 通知统计概览。 */
    public Map<String, Object> statistics() {
        List<Map<String, Object>> recent = adminMapper.selectNoticeRecentStats();
        List<Map<String, Object>> adminStats = adminMapper.selectNoticeAdminStats();
        Map<String, Object> summaryRow = adminMapper.selectNoticeSummary();

        long total = summaryRow == null ? 0 : toLong(summaryRow.get("total"));
        long readCount = summaryRow == null ? 0 : toLong(summaryRow.get("read_count"));
        double readRate = total > 0 ? Math.round((readCount * 100.0 / total) * 100.0) / 100.0 : 0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("readCount", readCount);
        summary.put("unreadCount", total - readCount);
        summary.put("readRate", readRate);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recent", recent);
        data.put("adminStats", adminStats);
        data.put("summary", summary);
        return data;
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Integer parseIntOrNull(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (Exception e) {
            return null;
        }
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

    private boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}
