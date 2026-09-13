package com.pg.pgfplots.dto.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 会话列表视图。 */
@Data
public class ConversationVO {

    @JsonProperty("conversation_id")
    private Integer conversationId;

    private String title;

    @JsonProperty("message_count")
    private Long messageCount;

    @JsonProperty("last_message")
    private String lastMessage;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("last_message_at")
    private LocalDateTime lastMessageAt;
}
