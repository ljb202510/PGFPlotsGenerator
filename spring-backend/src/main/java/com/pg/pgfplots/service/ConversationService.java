package com.pg.pgfplots.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.dto.conversation.ConversationMessageVO;
import com.pg.pgfplots.dto.conversation.ConversationVO;
import com.pg.pgfplots.entity.Conversation;
import com.pg.pgfplots.entity.ConversationMessage;
import com.pg.pgfplots.mapper.ConversationMapper;
import com.pg.pgfplots.mapper.ConversationMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话服务，对应 Node 的 {@code routes/conversations.js}。
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper conversationMessageMapper;

    /** 会话列表。 */
    public List<ConversationVO> list(Integer userId) {
        List<ConversationVO> rows = conversationMapper.selectConversationList(userId);
        rows.forEach(r -> {
            if (r.getLastMessage() == null) {
                r.setLastMessage("");
            }
        });
        return rows;
    }

    /** 新建会话。 */
    public Map<String, Object> create(Integer userId, String titleRaw) {
        String title = titleRaw == null ? "新对话" : titleRaw.trim();
        if (title.length() > 255) {
            title = title.substring(0, 255);
        }
        if (title.isEmpty()) {
            title = "新对话";
        }

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversationMapper.insert(conversation);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversation_id", conversation.getConversationId());
        data.put("title", title);
        return data;
    }

    /** 重命名会话。 */
    public void rename(Integer userId, String idRaw, String titleRaw) {
        Integer id = parseIdOrNull(idRaw);
        String title = titleRaw == null ? "" : titleRaw.trim();
        if (id == null || title.isEmpty()) {
            throw BusinessException.badRequest("缺少标题或ID无效");
        }
        conversationMapper.update(null, Wrappers.<Conversation>lambdaUpdate()
                .eq(Conversation::getConversationId, id)
                .eq(Conversation::getUserId, userId)
                .set(Conversation::getTitle, title.length() > 255 ? title.substring(0, 255) : title));
    }

    /** 删除会话及其消息（事务）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer userId, String idRaw) {
        Integer id = parseIdOrNull(idRaw);
        if (id == null) {
            throw BusinessException.badRequest("ID无效");
        }
        conversationMessageMapper.delete(Wrappers.<ConversationMessage>lambdaQuery()
                .eq(ConversationMessage::getConversationId, id)
                .eq(ConversationMessage::getUserId, userId));
        int affected = conversationMapper.delete(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getConversationId, id)
                .eq(Conversation::getUserId, userId));
        if (affected == 0) {
            throw BusinessException.notFound("对话不存在");
        }
    }

    /** 某会话的消息（正序）。 */
    public List<ConversationMessageVO> messages(Integer userId, String idRaw) {
        Integer id = parseIdOrNull(idRaw);
        if (id == null) {
            throw BusinessException.badRequest("ID无效");
        }
        return conversationMessageMapper.selectMessages(id, userId);
    }

    private Integer parseIdOrNull(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
