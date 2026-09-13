package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.dto.verification.SendCodeRequest;
import com.pg.pgfplots.dto.verification.VerifyCodeRequest;
import com.pg.pgfplots.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 邮箱验证码接口：/api/verification/*（免 token）。
 */
@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    /** 发送注册验证码 */
    @PostMapping("/send-register-code")
    public Result<Map<String, Object>> sendRegisterCode(@RequestBody SendCodeRequest request) {
        String email = request.getEmail();
        if (email == null || email.isEmpty()) {
            throw BusinessException.badRequest("请输入邮箱地址");
        }
        return Result.ok("验证码发送成功", verificationService.sendRegisterCode(email));
    }

    /** 校验注册验证码（一次性） */
    @PostMapping("/verify-register-code")
    public Result<Void> verifyRegisterCode(@RequestBody VerifyCodeRequest request) {
        String email = request.getEmail();
        String code = request.getCode();
        if (email == null || email.isEmpty() || code == null || code.isEmpty()) {
            throw BusinessException.badRequest("请提供邮箱和验证码");
        }
        if (!verificationService.verifyCode(email, code)) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }
        verificationService.deleteCode(email);
        return Result.okMsg("验证码验证成功");
    }
}
