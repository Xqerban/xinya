package com.xinya.dtx.common.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT 生成与校验工具
 */
@Component
public class JwtUtil {

    private final Algorithm algorithm;
    private final long accessTokenExpiresInSeconds;
    private final long refreshTokenExpiresInSeconds;

    public JwtUtil(
            @Value("${xinya.auth.jwt-secret:dev-secret-change-me}") String secret,
            @Value("${xinya.auth.access-token-exp-seconds:21600}") long accessTokenExpiresInSeconds,
            @Value("${xinya.auth.refresh-token-exp-seconds:604800}") long refreshTokenExpiresInSeconds
    ) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.accessTokenExpiresInSeconds = accessTokenExpiresInSeconds;
        this.refreshTokenExpiresInSeconds = refreshTokenExpiresInSeconds;
    }

    public String generateAccessToken(String userId, String username, String role, String phone) {
        return generateToken(userId, username, role, phone, "access", accessTokenExpiresInSeconds);
    }

    public String generateRefreshToken(String userId, String username, String role, String phone) {
        return generateToken(userId, username, role, phone, "refresh", refreshTokenExpiresInSeconds);
    }

    private String generateToken(String userId, String username, String role, String phone,
                                 String tokenType, long expiresInSeconds) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expiresInSeconds);

        return JWT.create()
                .withIssuer("xinya-dtx")
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
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

    public Map<String, Object> extractUserInfo(DecodedJWT jwt) {
        return Map.of(
                "userId", jwt.getSubject(),
                "username", jwt.getClaim("username").asString(),
                "role", jwt.getClaim("role").asString(),
                "phone", jwt.getClaim("phone").asString()
        );
    }

    public long getAccessTokenExpiresInSeconds() {
        return accessTokenExpiresInSeconds;
    }

    public long getRefreshTokenExpiresInSeconds() {
        return refreshTokenExpiresInSeconds;
    }
}

