package com.pg.pgfplots.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.dto.feedback.FeedbackVO;
import com.pg.pgfplots.dto.feedback.SubmitFeedbackRequest;
import com.pg.pgfplots.entity.Feedback;
import com.pg.pgfplots.entity.Notice;
import com.pg.pgfplots.mapper.FeedbackMapper;
import com.pg.pgfplots.mapper.NoticeMapper;
import com.pg.pgfplots.util.SystemLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 反馈服务，对应 Node 的 {@code routes/feedback.js}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final List<String> VALID_TYPES = List.of("suggestion", "ui", "bug", "other");

    private final FeedbackMapper feedbackMapper;
    private final NoticeMapper noticeMapper;
    private final SystemLogWriter systemLogWriter;

    /** 用户提交反馈，返回 feedback_id。 */
    public Integer submit(Integer userId, SubmitFeedbackRequest request) {
        String type = request.getType();
        String content = request.getContent();

        if (isBlank(type) || isBlank(content)) {
            systemLogWriter.warning("[FEEDBACK] 提交反馈校验失败-类型和内容不能为空 (用户 " + userId + ")");
            throw BusinessException.badRequest("反馈类型和内容不能为空");
        }
        if (content.length() > 100) {
            systemLogWriter.warning("[FEEDBACK] 提交反馈校验失败-内容超过100字 (用户 " + userId + ")");
            throw BusinessException.badRequest("反馈内容不能超过100字");
        }
        if (!VALID_TYPES.contains(type)) {
            systemLogWriter.warning("[FEEDBACK] 提交反馈校验失败-无效类型: " + type + " (用户 " + userId + ")");
            throw BusinessException.badRequest("无效的反馈类型");
        }

        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(type);
        feedback.setContent(content);
        feedbackMapper.insert(feedback);
        return feedback.getFeedbackId();
    }

    /** 用户自己的反馈列表（分页）。 */
    public Map<String, Object> myFeedbacks(Integer userId, String pageRaw, String limitRaw) {
        int page = Math.max(1, parseOrDefault(pageRaw, 1));
        int limit = Math.min(100, Math.max(1, parseOrDefault(limitRaw, 10)));
        int offset = (page - 1) * limit;

        List<FeedbackVO> records = feedbackMapper.selectUserFeedbacks(userId, limit, offset);
        long total = feedbackMapper.selectCount(Wrappers.<Feedback>lambdaQuery().eq(Feedback::getUserId, userId));
        return pageResult(records, page, limit, total);
    }

    /** 管理员反馈列表（分页 + 筛选）。 */
    public Map<String, Object> adminList(String pageRaw, String limitRaw, String type,
                                         String startDate, String endDate) {
        int page = Math.max(1, parseOrDefault(pageRaw, 1));
        int limit = Math.min(100, Math.max(1, parseOrDefault(limitRaw, 10)));
        int offset = (page - 1) * limit;

        List<FeedbackVO> records = feedbackMapper.selectAdminPage(type, startDate, endDate, limit, offset);
        long total = feedbackMapper.countAdmin(type, startDate, endDate);
        return pageResult(records, page, limit, total);
    }

    /** 反馈详情（管理员）。 */
    public FeedbackVO detail(String idRaw) {
        Integer id = parseIdOrNull(idRaw);
        FeedbackVO feedback = id == null ? null : feedbackMapper.selectDetail(id);
        if (feedback == null) {
            throw BusinessException.notFound("反馈不存在");
        }
        return feedback;
    }

    /** 管理员回复反馈（联动写入定向通知，feedback_id 唯一索引保证幂等）。 */
    public FeedbackVO reply(Integer adminId, String idRaw, String answer) {
        Integer id = parseIdOrNull(idRaw);
        if (answer == null || answer.trim().isEmpty()) {
            systemLogWriter.warning("[FEEDBACK] 回复校验失败-内容为空 (反馈 " + id + ", 管理员 " + adminId + ")");
            throw BusinessException.badRequest("回复内容不能为空");
        }
        if (answer.length() > 500) {
            systemLogWriter.warning("[FEEDBACK] 回复校验失败-内容超过500字 (反馈 " + id + ", 管理员 " + adminId + ")");
            throw BusinessException.badRequest("回复内容不能超过500字");
        }

        FeedbackVO existing = id == null ? null : feedbackMapper.selectDetail(id);
        if (existing == null) {
            throw BusinessException.notFound("反馈不存在");
        }

        String trimmed = answer.trim();
        feedbackMapper.update(null, Wrappers.<Feedback>lambdaUpdate()
                .eq(Feedback::getFeedbackId, id)
                .set(Feedback::getAnswer, trimmed)
                .set(Feedback::getAnswerTime, java.time.LocalDateTime.now()));

        FeedbackVO feedback = feedbackMapper.selectDetail(id);

        try {
            Notice notice = new Notice();
            notice.setTitle(titleOf(feedback.getType()));
            notice.setContent(feedback.getContent());
            notice.setAdminId(adminId);
            notice.setTargetUserId(feedback.getUserId());
            notice.setFeedbackId(feedback.getFeedbackId());
            notice.setFeedbackTime(feedback.getFeedbackTime());
            notice.setFeedbackType(feedback.getType());
            notice.setReply(trimmed);
            noticeMapper.insert(notice);
        } catch (Exception e) {
            // 通知写入失败不影响回复本身（feedback.answer 已更新）
            log.error("写入反馈通知失败: {}", e.getMessage());
        }

        return feedback;
    }

    /** 删除反馈（管理员）。 */
    public void delete(String idRaw) {
        Integer id = parseIdOrNull(idRaw);
        Feedback existing = id == null ? null : feedbackMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("反馈不存在");
        }
        feedbackMapper.deleteById(id);
    }

    private String titleOf(String type) {
        if (type == null) {
            return "其他反馈";
        }
        return switch (type) {
            case "suggestion" -> "建议反馈";
            case "ui" -> "界面反馈";
            case "bug" -> "BUG反馈";
            default -> "其他反馈";
        };
    }

    private Map<String, Object> pageResult(List<FeedbackVO> records, int page, int limit, long total) {
        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("total", total);
        pagination.put("pages", (long) Math.ceil((double) total / limit));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("pagination", pagination);
        return data;
    }

    private Integer parseIdOrNull(String raw) {
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
