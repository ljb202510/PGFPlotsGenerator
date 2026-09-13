package com.pg.pgfplots.security;

import com.pg.pgfplots.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 获取当前登录用户，未登录抛 401。 */
    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户未认证");
    }

    /** 当前登录用户 ID。 */
    public static Integer userId() {
        return currentUser().getUserId();
    }
}
