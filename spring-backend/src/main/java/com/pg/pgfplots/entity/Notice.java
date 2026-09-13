package com.pg.pgfplots.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 通知表 notice（系统广播 + 反馈回复统一存储） */
@Data
@TableName("notice")
public class Notice {

    @TableId(value = "notice_id", type = IdType.AUTO)
    private Integer noticeId;

    private String title;

    private String content;

    /** 发布通知的管理员 ID */
    private Integer adminId;

    /** 旧机制字段（现按 notice_read 每用户已读记录工作） */
    private Boolean isRead;

    private LocalDateTime createdTime;

    /** 定向接收用户；NULL 表示广播 */
    private Integer targetUserId;

    /** 关联来源反馈 ID；NULL 表示系统通知 */
    private Integer feedbackId;

    private LocalDateTime feedbackTime;

    /** suggestion | ui | bug | other */
    private String feedbackType;

    /** 管理员回复内容 */
    private String reply;
}
