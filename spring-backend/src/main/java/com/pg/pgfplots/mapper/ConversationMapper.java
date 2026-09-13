package com.pg.pgfplots.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pg.pgfplots.dto.conversation.ConversationVO;
import com.pg.pgfplots.entity.Conversation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 会话表 Mapper */
public interface ConversationMapper extends BaseMapper<Conversation> {

    /** 会话列表（含最后一条消息预览与消息数） */
    List<ConversationVO> selectConversationList(@Param("userId") Integer userId);
}
