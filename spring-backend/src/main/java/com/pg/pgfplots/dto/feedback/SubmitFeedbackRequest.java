package com.pg.pgfplots.dto.feedback;

import lombok.Data;

/** 提交反馈请求 */
@Data
public class SubmitFeedbackRequest {
    private String type;
    private String content;
}
