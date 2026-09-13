package com.pg.pgfplots.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pg.pgfplots.dto.conversation.ConversationMessageVO;
import com.pg.pgfplots.entity.ConversationMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 会话消息表 Mapper */
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {

    /** 某会话的消息（按 message_id 正序） */
    List<ConversationMessageVO> selectMessages(@Param("conversationId") Integer conversationId,
                                               @Param("userId") Integer userId);
}
