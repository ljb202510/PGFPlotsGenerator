package com.pg.pgfplots.dto.auth;

import lombok.Data;

/** 修改密码请求 */
@Data
public class ChangePasswordRequest {
    private String currentPassword;
    private String newPassword;
}
