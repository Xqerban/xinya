package com.xinya.ops.common.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

/**
 * JWT 生成与校验工具
 * 与 xinya-ruoyi-backend 共享同一密钥（JWT_SECRET 环境变量），token 可互认
 */
@Component
public class JwtUtil {

    private final Algorithm algorithm;
    private final long accessTokenExpiresInSeconds;
    private final long refreshTokenExpiresInSeconds;

    public JwtUtil(
            @Value("${xinya.auth.jwt-secret:xinya-ops-jwt-secret-key-2024-please-change-in-production}") String secret,
            @Value("${xinya.auth.access-token-exp-seconds:900}") long accessTokenExpiresInSeconds,
            @Value("${xinya.auth.refresh-token-exp-seconds:604800}") long refreshTokenExpiresInSeconds
    ) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.accessTokenExpiresInSeconds = accessTokenExpiresInSeconds;
        this.refreshTokenExpiresInSeconds = refreshTokenExpiresInSeconds;
    }

    public String generateAccessToken(String userId, String username, String role, String phone) {
        return buildToken(userId, username, role, phone, "access", accessTokenExpiresInSeconds);
    }

    public String generateRefreshToken(String userId, String username, String role, String phone) {
        return buildToken(userId, username, role, phone, "refresh", refreshTokenExpiresInSeconds);
    }

    private String buildToken(String userId, String username, String role, String phone,
                              String tokenType, long expiresInSeconds) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer("xinya-dtx")
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(expiresInSeconds)))
                .withSubject(userId)
                .withClaim("username", username)
                .withClaim("role", role)
                .withClaim("phone", phone)
                .withClaim("type", tokenType)
                .sign(algorithm);
    }

    public DecodedJWT verifyAccessToken(String token) throws JWTVerificationException {
        return verifyToken(token, "access");
    }

    public DecodedJWT verifyRefreshToken(String token) throws JWTVerificationException {
        return verifyToken(token, "refresh");
    }

    private DecodedJWT verifyToken(String token, String expectedType) throws JWTVerificationException {
        DecodedJWT jwt = JWT.require(algorithm)
                .withIssuer("xinya-dtx")
                .build()
                .verify(token);
        String type = jwt.getClaim("type").asString();
        if (!expectedType.equals(type)) {
            throw new JWTVerificationException("Invalid token type");
        }
        return jwt;
    }

    public long getAccessTokenExpiresInSeconds() {
        return accessTokenExpiresInSeconds;
    }

    public long getRefreshTokenExpiresInSeconds() {
        return refreshTokenExpiresInSeconds;
    }
}
