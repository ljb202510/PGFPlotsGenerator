package com.pg.pgfplots.security;

import com.pg.pgfplots.entity.User;
import com.pg.pgfplots.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析 Authorization: Bearer，查库装配 {@link LoginUser} 与角色权限。
 * <p>与原 Node 中间件一致：角色从数据库读取，不信任 JWT payload 中的角色声明。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 认证失败原因（供 EntryPoint 输出与原 Node 一致的消息） */
    public static final String ATTR_JWT_ERROR = "jwtError";

    private final JwtTokenProvider tokenProvider;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = header == null ? null : header.replace("Bearer ", "").trim();

        if (token == null || token.isEmpty()) {
            // 无 token：放行，由 Security 决定是否需要认证
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = tokenProvider.parse(token);
            Integer userId = tokenProvider.userIdOf(claims);
            User user = userId == null ? null : userMapper.selectById(userId);
            if (user == null) {
                request.setAttribute(ATTR_JWT_ERROR, "用户不存在");
            } else {
                LoginUser loginUser = new LoginUser(user.getUserId(), user.getUsername(), user.getEmail(), user.getRole());
                List<SimpleGrantedAuthority> authorities =
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase()));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            request.setAttribute(ATTR_JWT_ERROR, "token无效");
        }

        filterChain.doFilter(request, response);
    }
}
