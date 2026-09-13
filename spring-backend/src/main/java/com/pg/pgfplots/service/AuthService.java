package com.pg.pgfplots.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.dto.auth.AdminLoginRequest;
import com.pg.pgfplots.dto.auth.ChangeEmailRequest;
import com.pg.pgfplots.dto.auth.ChangePasswordRequest;
import com.pg.pgfplots.dto.auth.ChangeUsernameRequest;
import com.pg.pgfplots.dto.auth.LoginRequest;
import com.pg.pgfplots.dto.auth.RegisterRequest;
import com.pg.pgfplots.entity.User;
import com.pg.pgfplots.mapper.UserMapper;
import com.pg.pgfplots.security.LoginUser;
import com.pg.pgfplots.security.JwtTokenProvider;
import com.pg.pgfplots.util.SystemLogWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 认证服务：注册、登录、管理员登录、改密码/用户名/邮箱、token 校验。
 * <p>业务规则与消息文案尽可能与原 Node 版保持一致。</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9]{6,16}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\u4e00-\\u9fa5]{1,10}$");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final VerificationService verificationService;
    private final SystemLogWriter systemLogWriter;

    /** 管理员登录。 */
    public Map<String, Object> adminLogin(AdminLoginRequest request) {
        String adminAccount = request.getAdminAccount();
        String password = request.getPassword();
        if (isBlank(adminAccount) || isBlank(password)) {
            throw BusinessException.badRequest("请填写管理员账号和密码");
        }

        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, adminAccount)
                .eq(User::getRole, "admin"));
        if (user == null) {
            systemLogWriter.warning("[AUTH] 管理员登录失败-账号不存在或权限不足: " + adminAccount);
            throw BusinessException.badRequest("管理员账号不存在或权限不足");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            systemLogWriter.warning("[AUTH] 管理员登录失败-密码错误: " + adminAccount);
            throw BusinessException.badRequest("密码错误");
        }

        String token = tokenProvider.generateAdminToken(user.getUserId(), user.getEmail(), user.getRole());
        systemLogWriter.info("管理员登录成功: " + adminAccount + " (管理员ID: " + user.getUserId() + ")");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", userMap(user, true));
        data.put("token", token);
        return data;
    }

    /** 用户注册。 */
    public Map<String, Object> register(RegisterRequest request) {
        String username = request.getUsername();
        String email = request.getEmail();
        String password = request.getPassword();
        String verificationCode = request.getVerificationCode();
        if (isBlank(username) || isBlank(email) || isBlank(password) || isBlank(verificationCode)) {
            throw BusinessException.badRequest("请填写所有字段");
        }

        // 校验密码格式
        String pwdError = validatePassword(password);
        if (pwdError != null) {
            systemLogWriter.warning("[AUTH] 注册失败-密码格式错误: " + email);
            throw BusinessException.badRequest(pwdError);
        }

        // 用户名重复
        if (userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, username)) > 0) {
            systemLogWriter.warning("[AUTH] 注册失败-用户名已被注册: " + username);
            throw BusinessException.badRequest("该用户名已被注册");
        }

        // 校验邮箱验证码
        if (!verificationService.verifyCode(email, verificationCode)) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }

        // 邮箱重复
        if (userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getEmail, email)) > 0) {
            systemLogWriter.warning("[AUTH] 注册失败-邮箱已被注册: " + email);
            throw BusinessException.badRequest("该邮箱已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.insert(user);

        Integer userId = user.getUserId();
        verificationService.deleteCode(email);
        String token = tokenProvider.generateUserToken(userId, email);
        systemLogWriter.info("用户注册并登录成功: " + email + " (用户ID: " + userId + ")");

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("user_id", userId);
        userInfo.put("username", username);
        userInfo.put("email", email);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", userInfo);
        data.put("token", token);
        return data;
    }

    /** 用户登录。 */
    public Map<String, Object> login(LoginRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();
        if (isBlank(email) || isBlank(password)) {
            throw BusinessException.badRequest("请填写邮箱和密码");
        }

        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
        if (user == null) {
            systemLogWriter.warning("[AUTH] 用户登录失败-账号不存在: " + email);
            throw BusinessException.badRequest("用户不存在");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            systemLogWriter.warning("[AUTH] 用户登录失败-密码错误: " + email);
            throw BusinessException.badRequest("密码错误");
        }

        String token = tokenProvider.generateUserToken(user.getUserId(), user.getEmail());
        systemLogWriter.info("用户登录成功: " + user.getEmail() + " (用户ID: " + user.getUserId() + ")");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", userMap(user, false));
        data.put("token", token);
        return data;
    }

    /** 修改密码。 */
    public void changePassword(Integer userId, ChangePasswordRequest request) {
        String currentPassword = request.getCurrentPassword();
        String newPassword = request.getNewPassword();
        if (isBlank(currentPassword) || isBlank(newPassword)) {
            throw BusinessException.badRequest("请填写当前密码和新密码");
        }

        String pwdError = validatePassword(newPassword);
        if (pwdError != null) {
            throw BusinessException.badRequest(pwdError);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw BusinessException.badRequest("当前密码错误");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw BusinessException.badRequest("新密码不能与当前密码相同");
        }

        User update = new User();
        update.setUserId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
    }

    /** 修改用户名，返回 {oldUsername, newUsername}。 */
    public Map<String, Object> changeUsername(Integer userId, ChangeUsernameRequest request) {
        String newUsername = request.getNewUsername();
        if (isBlank(newUsername) || newUsername.trim().isEmpty()) {
            throw BusinessException.badRequest("请输入新用户名");
        }

        String trimmed = newUsername.trim();
        if (trimmed.length() > 10) {
            throw BusinessException.badRequest("用户名最长为10个字符");
        }
        if (!USERNAME_PATTERN.matcher(trimmed).matches()) {
            throw BusinessException.badRequest("用户名只能包含字母、数字、下划线和中文字符");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (trimmed.equals(user.getUsername())) {
            throw BusinessException.badRequest("新用户名不能与当前用户名相同");
        }
        if (userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, trimmed)
                .ne(User::getUserId, userId)) > 0) {
            throw BusinessException.badRequest("该用户名已被其他用户使用，请换一个试试");
        }

        User update = new User();
        update.setUserId(userId);
        update.setUsername(trimmed);
        userMapper.updateById(update);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("oldUsername", user.getUsername());
        data.put("newUsername", trimmed);
        return data;
    }

    /** 修改邮箱，返回 {oldEmail, newEmail}。 */
    public Map<String, Object> changeEmail(Integer userId, ChangeEmailRequest request) {
        String newEmail = request.getNewEmail();
        String verificationCode = request.getVerificationCode();
        if (isBlank(newEmail) || isBlank(verificationCode)) {
            throw BusinessException.badRequest("请填写新邮箱、验证码和当前密码");
        }
        if (!verificationService.isValidEmail(newEmail)) {
            throw BusinessException.badRequest("邮箱格式不正确");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (newEmail.equals(user.getEmail())) {
            throw BusinessException.badRequest("新邮箱不能与当前邮箱相同");
        }
        if (!verificationService.verifyCode(newEmail, verificationCode)) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }
        if (userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getEmail, newEmail)
                .ne(User::getUserId, userId)) > 0) {
            throw BusinessException.badRequest("该邮箱已被其他用户使用");
        }

        User update = new User();
        update.setUserId(userId);
        update.setEmail(newEmail);
        userMapper.updateById(update);
        verificationService.deleteCode(newEmail);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("oldEmail", user.getEmail());
        data.put("newEmail", newEmail);
        return data;
    }

    /** token 校验：返回当前用户基本信息。 */
    public Map<String, Object> validate(LoginUser loginUser) {
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("user_id", loginUser.getUserId());
        userInfo.put("username", loginUser.getUsername());
        userInfo.put("email", loginUser.getEmail());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", userInfo);
        return data;
    }

    private Map<String, Object> userMap(User user, boolean includeRole) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("user_id", user.getUserId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        if (includeRole) {
            map.put("role", user.getRole());
        }
        return map;
    }

    /** 密码校验：仅字母和数字，长度 6-16 位；返回错误消息，合法返回 null。 */
    private String validatePassword(String pwd) {
        if (pwd == null || pwd.isEmpty()) {
            return "密码不能为空";
        }
        if (!PASSWORD_PATTERN.matcher(pwd).matches()) {
            return "密码只能包含字母和数字，长度 6-16 位";
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}
