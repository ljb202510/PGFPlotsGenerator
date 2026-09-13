package com.pg.pgfplots.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 邮箱验证码表 email_verification_codes */
@Data
@TableName("email_verification_codes")
public class EmailVerificationCode {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String email;

    /** 6 位数字验证码 */
    private String code;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;
}
