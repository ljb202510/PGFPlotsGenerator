package com.pg.pgfplots.dto.notice;

import lombok.Data;

import java.time.LocalDateTime;

/** 通知表查询行（内部使用）。 */
@Data
public class NoticeRowVO {

    private Integer noticeId;

    private String title;

    private String content;

    private LocalDateTime createdTime;

    private Integer feedbackId;

    private LocalDateTime feedbackTime;

    private String feedbackType;

    private String reply;

    /** 1=已读 */
    private Integer isRead;
}
