package com.pg.pgfplots.dto.auth;

import lombok.Data;

/** 修改邮箱请求 */
@Data
public class ChangeEmailRequest {
    private String newEmail;
    private String verificationCode;
}
