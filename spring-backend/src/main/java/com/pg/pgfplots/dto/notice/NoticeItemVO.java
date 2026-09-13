package com.pg.pgfplots.dto.notice;

import lombok.Data;

import java.time.LocalDateTime;

/** 通知列表项（返回给前端）。 */
@Data
public class NoticeItemVO {

    private Integer id;

    /** system | feedback */
    private String type;

    private String title;

    private String content;

    private LocalDateTime time;

    private Boolean isRead;

    private LocalDateTime feedbackTime;

    private String feedbackType;

    private String reply;
}
