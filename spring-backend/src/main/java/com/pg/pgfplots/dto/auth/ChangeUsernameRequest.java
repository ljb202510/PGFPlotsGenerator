package com.pg.pgfplots.dto.auth;

import lombok.Data;

/** 修改用户名请求 */
@Data
public class ChangeUsernameRequest {
    private String newUsername;
}
