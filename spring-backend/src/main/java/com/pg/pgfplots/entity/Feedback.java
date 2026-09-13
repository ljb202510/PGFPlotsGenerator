package com.pg.pgfplots.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 反馈表 feedback */
@Data
@TableName("feedback")
public class Feedback {

    @TableId(value = "feedback_id", type = IdType.AUTO)
    private Integer feedbackId;

    private Integer userId;

    /** suggestion | ui | bug | other */
    private String type;

    private String content;

    private LocalDateTime feedbackTime;

    /** 管理员回复内容 */
    private String answer;

    private LocalDateTime answerTime;
}
