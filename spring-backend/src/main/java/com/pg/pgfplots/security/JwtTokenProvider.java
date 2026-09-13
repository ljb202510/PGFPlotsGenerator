package com.pg.pgfplots.security;

import com.pg.pgfplots.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 签发与校验。
 * <p>有效期与原 Node 版一致：普通用户 24h、管理员 7d。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_IS_ADMIN = "isAdmin";

    private final AppProperties appProperties;

    private SecretKey key;

    @PostConstruct
    void init() {
        String secret = appProperties.getJwt().getSecret();
        // fail-fast：与 Node app.js 一致，缺少 JWT_SECRET 启动即失败
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("缺少环境变量 JWT_SECRET：请设置后重试。");
        }
        // 复用 Node 的 JWT_SECRET；HS256 要求密钥 >= 32 字节，短密钥用 SHA-256 派生保证强度
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            try {
                bytes = MessageDigest.getInstance("SHA-256").digest(bytes);
            } catch (Exception e) {
                throw new IllegalStateException("JWT 密钥派生失败", e);
            }
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /** 签发普通用户 token（24h）。 */
    public String generateUserToken(Integer userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_EMAIL, email);
        long ttl = appProperties.getJwt().getUserExpireHours() * 3600_000L;
        return build(claims, ttl);
    }

    /** 签发管理员 token（7d）。 */
    public String generateAdminToken(Integer userId, String email, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_EMAIL, email);
        claims.put(CLAIM_ROLE, role);
        claims.put(CLAIM_IS_ADMIN, true);
        long ttl = appProperties.getJwt().getAdminExpireDays() * 24 * 3600_000L;
        return build(claims, ttl);
    }

    private String build(Map<String, Object> claims, long ttlMillis) {
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token。
     *
     * @throws io.jsonwebtoken.JwtException 无效或过期
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 claims 取 userId。 */
    public Integer userIdOf(Claims claims) {
        Object value = claims.get(CLAIM_USER_ID);
        return value instanceof Number number ? number.intValue() : null;
    }
}
