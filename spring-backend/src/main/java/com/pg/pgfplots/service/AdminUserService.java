package com.pg.pgfplots.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.dto.admin.AdminUserVO;
import com.pg.pgfplots.entity.ApiLog;
import com.pg.pgfplots.entity.DataFile;
import com.pg.pgfplots.entity.EmailVerificationCode;
import com.pg.pgfplots.entity.Feedback;
import com.pg.pgfplots.entity.GenerationHistory;
import com.pg.pgfplots.entity.User;
import com.pg.pgfplots.mapper.AdminMapper;
import com.pg.pgfplots.mapper.ApiLogMapper;
import com.pg.pgfplots.mapper.DataFileMapper;
import com.pg.pgfplots.mapper.EmailVerificationCodeMapper;
import com.pg.pgfplots.mapper.FeedbackMapper;
import com.pg.pgfplots.mapper.GenerationHistoryMapper;
import com.pg.pgfplots.mapper.UserMapper;
import com.pg.pgfplots.util.TimeFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员-用户管理，对应 Node 的 {@code routes/AdminUser.js}。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;
    private final ApiLogMapper apiLogMapper;
    private final FeedbackMapper feedbackMapper;
    private final GenerationHistoryMapper historyMapper;
    private final DataFileMapper dataFileMapper;
    private final EmailVerificationCodeMapper codeMapper;
    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;

    /** 用户列表（分页 + 关键字）。 */
    public Map<String, Object> list(String pageRaw, String pageSizeRaw, String keyword) {
        int page = Math.max(1, parseOrDefault(pageRaw, 1));
        int pageSize = Math.max(1, parseOrDefault(pageSizeRaw, 10));
        int offset = (page - 1) * pageSize;
        boolean hasKeyword = keyword != null && !keyword.isEmpty();

        var query = Wrappers.<User>lambdaQuery()
                .select(User::getUserId, User::getUsername, User::getEmail, User::getRole, User::getRegisterTime)
                .orderByDesc(User::getRegisterTime)
                .last("LIMIT " + pageSize + " OFFSET " + offset);
        if (hasKeyword) {
            query.and(w -> w.like(User::getUsername, keyword).or().like(User::getEmail, keyword));
        }
        List<User> users = userMapper.selectList(query);

        var countQuery = Wrappers.<User>lambdaQuery();
        if (hasKeyword) {
            countQuery.and(w -> w.like(User::getUsername, keyword).or().like(User::getEmail, keyword));
        }
        long total = userMapper.selectCount(countQuery);

        List<AdminUserVO> vos = new ArrayList<>(users.size());
        for (User user : users) {
            AdminUserVO vo = new AdminUserVO();
            vo.setUserId(user.getUserId());
            vo.setUsername(user.getUsername());
            vo.setEmail(user.getEmail());
            vo.setRole(user.getRole());
            vo.setRegisterTime(TimeFormat.toSecond(user.getRegisterTime()));
            vos.add(vo);
        }

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", page);
        pagination.put("pageSize", pageSize);
        pagination.put("total", total);
        pagination.put("totalPages", (long) Math.ceil((double) total / pageSize));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users", vos);
        data.put("pagination", pagination);
        return data;
    }

    /** 重置密码为 666666。 */
    public Map<String, Object> resetPassword(String idRaw) {
        Integer userId = parseIntOrNull(idRaw);
        if (userId == null) {
            throw BusinessException.badRequest("用户ID必须是数字");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        User update = new User();
        update.setUserId(userId);
        update.setPassword(passwordEncoder.encode("666666"));
        userMapper.updateById(update);
        return userSummary(user);
    }

    /** 删除用户（级联删除关联数据）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> delete(Integer currentAdminId, String idRaw) {
        Integer userId = parseIntOrNull(idRaw);
        if (userId == null) {
            throw BusinessException.badRequest("参数错误");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (userId.equals(currentAdminId)) {
            throw BusinessException.badRequest("不能删除自己");
        }

        apiLogMapper.delete(Wrappers.<ApiLog>lambdaQuery().eq(ApiLog::getUserId, userId));
        feedbackMapper.delete(Wrappers.<Feedback>lambdaQuery().eq(Feedback::getUserId, userId));
        historyMapper.delete(Wrappers.<GenerationHistory>lambdaQuery().eq(GenerationHistory::getUserId, userId));
        dataFileMapper.delete(Wrappers.<DataFile>lambdaQuery().eq(DataFile::getUserId, userId));
        codeMapper.delete(Wrappers.<EmailVerificationCode>lambdaQuery().eq(EmailVerificationCode::getEmail, user.getEmail()));
        userMapper.deleteById(userId);
        return userSummary(user);
    }

    /** 用户统计概览。 */
    public Map<String, Object> statistics() {
        List<Map<String, Object>> recent = adminMapper.selectRecentRegistrations();
        List<Map<String, Object>> roleStats = adminMapper.selectRoleStats();
        Map<String, Object> summaryRow = adminMapper.selectUserSummary();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", summaryRow == null ? 0 : summaryRow.get("total"));
        summary.put("admin_count", summaryRow == null ? 0 : summaryRow.get("admin_count"));
        summary.put("user_count", summaryRow == null ? 0 : summaryRow.get("user_count"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recent", recent);
        data.put("roleStats", roleStats);
        data.put("summary", summary);
        return data;
    }

    private Map<String, Object> userSummary(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", user.getUserId());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        return data;
    }

    private Integer parseIntOrNull(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private int parseOrDefault(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
