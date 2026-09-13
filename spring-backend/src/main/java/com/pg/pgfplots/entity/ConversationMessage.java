package com.pg.pgfplots.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 会话消息表 conversation_messages */
@Data
@TableName("conversation_messages")
public class ConversationMessage {

    @TableId(value = "message_id", type = IdType.AUTO)
    private Integer messageId;

    private Integer conversationId;

    private Integer userId;

    /** user | assistant */
    private String role;

    private String content;

    /** assistant 消息附带的图表代码 */
    private String chartCode;

    private Integer historyId;

    /** 用户选择的数据集（JSON 字符串） */
    private String selectedFiles;

    private LocalDateTime createdAt;
}
