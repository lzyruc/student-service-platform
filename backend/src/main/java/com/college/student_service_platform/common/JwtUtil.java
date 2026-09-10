package com.college.student_service_platform.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final String issuer;
    private final long defaultTtlSeconds;

    public JwtUtil(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer:student-service-platform}") String issuer,
            @Value("${security.jwt.ttl:24h}") Duration defaultTtl
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET 必须至少包含 32 个 UTF-8 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer == null || issuer.isBlank() ? "student-service-platform" : issuer.trim();
        this.defaultTtlSeconds = Math.max(60, defaultTtl.toSeconds());
    }

    public String createToken(String subject, String roleCode) {
        return createToken(subject, roleCode, defaultTtlSeconds);
    }

    public String createToken(String subject, String roleCode, long ttlSeconds) {
        if (subject == null || subject.isBlank() || roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("JWT subject 和 role 不能为空");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(Math.max(60, ttlSeconds));
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(subject.trim())
                .claim("role", roleCode.trim())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public Map<String, Object> verifyToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token 为空");
        }
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token.trim())
                .getPayload();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(claims);
        payload.put("sub", claims.getSubject());
        payload.put("iat", claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant().getEpochSecond());
        payload.put("exp", claims.getExpiration() == null ? null : claims.getExpiration().toInstant().getEpochSecond());
        return payload;
    }
}
