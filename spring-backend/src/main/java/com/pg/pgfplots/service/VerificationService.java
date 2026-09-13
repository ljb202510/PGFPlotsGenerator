package com.pg.pgfplots.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.entity.EmailVerificationCode;
import com.pg.pgfplots.entity.User;
import com.pg.pgfplots.mapper.EmailVerificationCodeMapper;
import com.pg.pgfplots.mapper.UserMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 邮箱验证码服务，对应 Node 的 {@code services/verificationService.js}。
 * <p>6 位数字验证码，默认 10 分钟有效、一次性。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationCodeMapper codeMapper;
    private final UserMapper userMapper;
    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    /** 生成 6 位数字验证码。 */
    public String generateCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /** 存储验证码：先删同邮箱旧码，再插入新码。 */
    public void storeCode(String email, String code) {
        codeMapper.delete(Wrappers.<EmailVerificationCode>lambdaQuery().eq(EmailVerificationCode::getEmail, email));
        EmailVerificationCode entity = new EmailVerificationCode();
        entity.setEmail(email);
        entity.setCode(code);
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(appProperties.getMail().getCodeExpireMinutes()));
        codeMapper.insert(entity);
    }

    /** 校验验证码是否有效（未过期）。 */
    public boolean verifyCode(String email, String code) {
        Long count = codeMapper.selectCount(Wrappers.<EmailVerificationCode>lambdaQuery()
                .eq(EmailVerificationCode::getEmail, email)
                .eq(EmailVerificationCode::getCode, code)
                .gt(EmailVerificationCode::getExpiresAt, LocalDateTime.now()));
        return count != null && count > 0;
    }

    /** 删除指定邮箱的验证码。 */
    public void deleteCode(String email) {
        codeMapper.delete(Wrappers.<EmailVerificationCode>lambdaQuery().eq(EmailVerificationCode::getEmail, email));
    }

    /**
     * 发送注册验证码（主流程）。
     *
     * @return 与 Node 一致的 {success, email, expires_in}
     */
    public Map<String, Object> sendRegisterCode(String email) {
        if (!isValidEmail(email)) {
            throw BusinessException.badRequest("邮箱格式不正确");
        }
        Long existing = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
        if (existing != null && existing > 0) {
            throw BusinessException.badRequest("该邮箱已被注册");
        }

        String code = generateCode();
        storeCode(email, code);

        try {
            sendRegisterVerificationEmail(email, code);
        } catch (Exception e) {
            log.error("发送验证码邮件失败: {}", e.getMessage());
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "发送验证码失败，请稍后重试");
        }

        log.info("注册验证码发送成功：{}", email);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("email", email);
        result.put("expires_in", appProperties.getMail().getCodeExpireMinutes() * 60);
        return result;
    }

    /** 发送注册验证码邮件（HTML 模板，与原 Node 版一致）。 */
    private void sendRegisterVerificationEmail(String email, String code) throws Exception {
        String subject = "账号注册验证码";
        String text = "您的注册验证码是：" + code + "，该验证码10分钟内有效。";

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(normalizeFrom(appProperties.getMail().getFrom()));
        helper.setTo(email);
        helper.setSubject(subject);
        helper.setText(text, buildEmailTemplate(subject, text, code));
        mailSender.send(message);
    }

    /** 去掉 .env 中可能残留的首尾引号。 */
    private String normalizeFrom(String from) {
        if (from == null) {
            return null;
        }
        String trimmed = from.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** 邮箱格式校验。 */
    public boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    private String buildEmailTemplate(String subject, String text, String code) {
        return "<!DOCTYPE html>"
                + "<html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>注册验证码</title></head>"
                + "<body style=\"margin:0;padding:0;background-color:#f5f5f5;font-family:Arial,'Microsoft YaHei',sans-serif;\">"
                + "<table width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" bgcolor=\"#f5f5f5\"><tr><td align=\"center\" style=\"padding:20px 0;\">"
                + "<table width=\"600\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" bgcolor=\"#ffffff\" style=\"border-collapse:collapse;box-shadow:0 2px 8px rgba(0,0,0,0.1);\">"
                + "<tr><td style=\"padding:40px 30px;text-align:center;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);\">"
                + "<h1 style=\"margin:0;font-size:28px;color:white;font-weight:bold;\">注册验证码</h1></td></tr>"
                + "<tr><td style=\"padding:40px 30px;font-size:16px;line-height:1.6;color:#333;\">"
                + "<h2 style=\"color:#333;margin-top:0;margin-bottom:20px;\">" + subject + "</h2>"
                + "<p style=\"margin-bottom:15px;\">尊敬的用户，您好！</p>"
                + "<p style=\"margin-bottom:30px;\">" + text + "</p>"
                + "<table width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\"><tr><td align=\"center\">"
                + "<div style=\"background:white;padding:25px;margin:25px 0;text-align:center;border-radius:8px;border:2px dashed #1890ff;\">"
                + "<span style=\"font-size:36px;font-weight:bold;color:#1890ff;letter-spacing:8px;line-height:1.2;\">" + code + "</span>"
                + "</div></td></tr></table>"
                + "<table width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" bgcolor=\"#fff8e1\" style=\"border-left:4px solid #ffd54f;\">"
                + "<tr><td style=\"padding:15px;\"><p style=\"color:#e65100;font-size:14px;line-height:1.5;margin:0;\">"
                + "<strong>重要提示：</strong>请勿将验证码泄露给他人。此验证码10分钟内有效，如非本人操作，请立即忽略此邮件。</p>"
                + "</td></tr></table></td></tr>"
                + "<tr><td bgcolor=\"#f5f5f5\" style=\"padding:20px;text-align:center;color:#999;font-size:12px;\">"
                + "<p style=\"margin:0 0 5px 0;\">此邮件由系统自动发送，请勿回复。</p>"
                + "<p style=\"margin:0;\">如果您有任何疑问，请联系客服</p></td></tr>"
                + "</table></td></tr></table></body></html>";
    }
}
