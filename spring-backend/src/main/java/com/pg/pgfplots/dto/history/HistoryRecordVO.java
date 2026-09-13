package com.pg.pgfplots.dto.history;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史记录返回视图（字段名保持 Node 契约以兼容前端）。
 */
@Data
public class HistoryRecordVO {

    private Integer id;

    @JsonProperty("history_id")
    private Integer historyId;

    @JsonProperty("user_id")
    private Integer userId;

    @JsonProperty("data_id")
    private Integer dataId;

    private String description;

    @JsonProperty("chart_code")
    private String chartCode;

    @JsonProperty("user_input")
    private String userInput;

    @JsonProperty("ai_response")
    private String aiResponse;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("generation_path")
    private String generationPath;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
