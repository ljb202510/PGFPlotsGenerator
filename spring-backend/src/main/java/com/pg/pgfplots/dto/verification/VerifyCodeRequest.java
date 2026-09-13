package com.pg.pgfplots.dto.verification;

import lombok.Data;

/** 校验验证码请求 */
@Data
public class VerifyCodeRequest {
    private String email;
    private String code;
}
