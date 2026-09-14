package com.pg.pgfplots.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** AI 图表生成请求 */
@Data
public class ChatRequest {

    /** 用户消息（必填） */
    private String message;

    /** 数据集 ID 列表 */
    @JsonProperty("data_ids")
    private List<Integer> dataIds;

    /** 前端直接传入的图表代码（优先于从回复提取） */
    @JsonProperty("chart_code")
    private String chartCode;

    /** 模型通道：qwen | siliconflow | deepseek（缺省或未知值回落 qwen） */
    private String model;

    /** 会话 ID（传入则持久化对话） */
    @JsonProperty("conversation_id")
    private Integer conversationId;

    /** 用户选择的数据集（写入会话消息） */
    @JsonProperty("selected_files")
    private List<Object> selectedFiles;
}
