package com.pg.pgfplots.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 会话表 conversations */
@Data
@TableName("conversations")
public class Conversation {

    @TableId(value = "conversation_id", type = IdType.AUTO)
    private Integer conversationId;

    private Integer userId;

    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
