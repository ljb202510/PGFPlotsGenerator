package com.pg.pgfplots.dto.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 反馈视图（含管理员查询时的 username 与用户查询时的 status）。 */
@Data
public class FeedbackVO {

    @JsonProperty("feedback_id")
    private Integer feedbackId;

    @JsonProperty("user_id")
    private Integer userId;

    /** suggestion | ui | bug | other */
    private String type;

    private String content;

    @JsonProperty("feedback_time")
    private LocalDateTime feedbackTime;

    private String answer;

    @JsonProperty("answer_time")
    private LocalDateTime answerTime;

    /** 已回复 | 待回复（仅用户端列表查询返回） */
    private String status;

    /** 提交者用户名（仅管理员端查询返回） */
    private String username;
}
