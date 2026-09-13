package com.pg.pgfplots.security;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户（由 JWT 解析后查库装配），对应 Node 的 {@code req.user} + 查库得到的 role。
 */
@Data
@AllArgsConstructor
public class LoginUser implements Serializable {

    private Integer userId;

    private String username;

    private String email;

    /** user | admin */
    private String role;
}
