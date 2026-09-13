package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.dto.auth.AdminLoginRequest;
import com.pg.pgfplots.dto.auth.ChangeEmailRequest;
import com.pg.pgfplots.dto.auth.ChangePasswordRequest;
import com.pg.pgfplots.dto.auth.ChangeUsernameRequest;
import com.pg.pgfplots.dto.auth.LoginRequest;
import com.pg.pgfplots.dto.auth.RegisterRequest;
import com.pg.pgfplots.security.SecurityUtils;
import com.pg.pgfplots.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口：/api/auth/*。
 * <p>统一响应体 {@code {success, data, message}}（原 Node 把 user/token 放在顶层，现移入 data）。</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 管理员登录 */
    @PostMapping("/admin/login")
    public Result<Map<String, Object>> adminLogin(@RequestBody AdminLoginRequest request) {
        return Result.ok("管理员登录成功", authService.adminLogin(request));
    }

    /** 用户注册 */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        return Result.ok("注册成功", authService.register(request));
    }

    /** 用户登录 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        return Result.ok("登录成功", authService.login(request));
    }

    /** 修改密码 */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecurityUtils.userId(), request);
        return Result.ok("密码修改成功", null);
    }

    /** 修改用户名 */
    @PostMapping("/change-username")
    public Result<Map<String, Object>> changeUsername(@RequestBody ChangeUsernameRequest request) {
        return Result.ok("用户名修改成功", authService.changeUsername(SecurityUtils.userId(), request));
    }

    /** 修改邮箱 */
    @PostMapping("/change-email")
    public Result<Map<String, Object>> changeEmail(@RequestBody ChangeEmailRequest request) {
        return Result.ok("邮箱修改成功", authService.changeEmail(SecurityUtils.userId(), request));
    }

    /** 校验 token 并返回当前用户 */
    @GetMapping("/validate")
    public Result<Map<String, Object>> validate() {
        return Result.ok(authService.validate(SecurityUtils.currentUser()));
    }
}
