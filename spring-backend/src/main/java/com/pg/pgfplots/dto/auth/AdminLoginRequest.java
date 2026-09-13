package com.pg.pgfplots.dto.auth;

import lombok.Data;

/** 管理员登录请求 */
@Data
public class AdminLoginRequest {
    private String adminAccount;
    private String password;
}
