package com.pg.pgfplots.dto.verification;

import lombok.Data;

/** 发送验证码请求 */
@Data
public class SendCodeRequest {
    private String email;
}
