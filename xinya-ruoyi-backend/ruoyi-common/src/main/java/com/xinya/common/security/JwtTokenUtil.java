package com.xinya.common.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

/**
 * JWT 生成与校验工具（替代原 JwtUtil，逻辑一致）
 */
@Component
public class JwtTokenUtil {

    private final Algorithm algorithm;
    private final long accessTokenExpSeconds;
    private final long refreshTokenExpSeconds;

    public JwtTokenUtil(
            @Value("${xinya.auth.jwt-secret:dev-secret-change-me}") String secret,
            @Value("${xinya.auth.access-token-exp-seconds:900}") long accessTokenExpSeconds,
            @Value("${xinya.auth.refresh-token-exp-seconds:604800}") long refreshTokenExpSeconds) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.accessTokenExpSeconds = accessTokenExpSeconds;
        this.refreshTokenExpSeconds = refreshTokenExpSeconds;
    }

    public String generateAccessToken(String userId, String username, String role, String phone) {
        return buildToken(userId, username, role, phone, "access", accessTokenExpSeconds);
    }

    public String generateRefreshToken(String userId, String username, String role, String phone) {
        return buildToken(userId, username, role, phone, "refresh", refreshTokenExpSeconds);
    }

    private String buildToken(String userId, String username, String role, String phone,
                              String tokenType, long expSeconds) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer("xinya-dtx")
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(expSeconds)))
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

    public long getAccessTokenExpSeconds() {
        return accessTokenExpSeconds;
    }

    public long getRefreshTokenExpSeconds() {
        return refreshTokenExpSeconds;
    }
}
