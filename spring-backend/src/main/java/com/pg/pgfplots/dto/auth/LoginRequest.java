package com.pg.pgfplots.dto.auth;

import lombok.Data;

/** 用户登录请求 */
@Data
public class LoginRequest {
    private String email;
    private String password;
}
