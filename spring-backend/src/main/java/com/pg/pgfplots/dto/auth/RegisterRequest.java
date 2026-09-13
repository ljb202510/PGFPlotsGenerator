package com.pg.pgfplots.dto.auth;

import lombok.Data;

/** 用户注册请求 */
@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private String verificationCode;
}
