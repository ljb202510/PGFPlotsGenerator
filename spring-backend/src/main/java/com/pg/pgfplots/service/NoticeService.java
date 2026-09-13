package com.pg.pgfplots.service;

import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.dto.notice.NoticeItemVO;
import com.pg.pgfplots.dto.notice.NoticeRowVO;
import com.pg.pgfplots.mapper.NoticeMapper;
import com.pg.pgfplots.mapper.NoticeReadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知服务，对应 Node 的 {@code routes/notice.js}。
 * <p>系统广播（target_user_id NULL）与反馈回复（定向）统一走 notice + notice_read。</p>
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeMapper noticeMapper;
    private final NoticeReadMapper noticeReadMapper;

    /** 用户通知列表（含未读数）。 */
    public Map<String, Object> list(Integer userId) {
        List<NoticeRowVO> rows = noticeMapper.selectUserNotices(userId);
        List<NoticeItemVO> notices = new ArrayList<>(rows.size());
        long unreadCount = 0;

        for (NoticeRowVO row : rows) {
            boolean isFeedback = row.getFeedbackId() != null;
            boolean isRead = row.getIsRead() != null && row.getIsRead() == 1;

            LocalDateTime time = isFeedback
                    ? (row.getFeedbackTime() != null ? row.getFeedbackTime() : row.getCreatedTime())
                    : row.getCreatedTime();

            NoticeItemVO item = new NoticeItemVO();
            item.setId(row.getNoticeId());
            item.setType(isFeedback ? "feedback" : "system");
            item.setTitle(row.getTitle());
            item.setContent(row.getContent());
            item.setTime(time);
            item.setIsRead(isRead);
            item.setFeedbackTime(isFeedback ? time : null);
            item.setFeedbackType(row.getFeedbackType());
            item.setReply(row.getReply());
            notices.add(item);

            if (!isRead) {
                unreadCount++;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notices", notices);
        data.put("unreadCount", unreadCount);
        data.put("totalCount", notices.size());
        return data;
    }

    /** 标记单条通知已读。 */
    public void markRead(Integer userId, String idRaw) {
        Integer noticeId = parseIdOrNull(idRaw);
        if (noticeId == null) {
            throw BusinessException.badRequest("无效的通知 ID");
        }
        noticeReadMapper.insertIgnore(userId, noticeId);
    }

    /** 标记所有可见通知已读。 */
    public void markAllRead(Integer userId) {
        noticeReadMapper.insertIgnoreAll(userId);
    }

    /** 未读数量。 */
    public Map<String, Object> unreadCount(Integer userId) {
        Map<String, Object> row = noticeMapper.countUnread(userId);
        long unread = 0;
        if (row != null && row.get("unread") instanceof Number number) {
            unread = number.longValue();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("unreadCount", unread);
        data.put("feedbackUnread", unread);
        data.put("systemUnread", 0);
        return data;
    }

    private Integer parseIdOrNull(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
