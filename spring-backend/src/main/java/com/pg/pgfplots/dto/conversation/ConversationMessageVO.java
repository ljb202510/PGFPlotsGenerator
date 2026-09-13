package com.pg.pgfplots.dto.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 会话消息视图。 */
@Data
public class ConversationMessageVO {

    @JsonProperty("message_id")
    private Integer messageId;

    private String role;

    private String content;

    @JsonProperty("chart_code")
    private String chartCode;

    @JsonProperty("history_id")
    private Integer historyId;

    @JsonProperty("selected_files")
    private String selectedFiles;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
