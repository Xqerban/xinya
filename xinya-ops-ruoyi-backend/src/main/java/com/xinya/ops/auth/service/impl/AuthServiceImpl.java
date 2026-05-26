package com.xinya.ops.auth.service.impl;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xinya.ops.auth.dto.LoginResponse;
import com.xinya.ops.auth.service.AuthService;
import com.xinya.ops.common.security.JwtUtil;
import com.xinya.ops.user.entity.OpUser;
import com.xinya.ops.user.mapper.OpUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final OpUserMapper opUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public LoginResponse loginByUsername(String username, String password) {
        OpUser user = opUserMapper.selectOne(
                new LambdaQueryWrapper<OpUser>().eq(OpUser::getUsername, username));
        return doLogin(user, password);
    }

    @Override
    @Transactional
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken 不能为空");
        }
        DecodedJWT jwt;
        try {
            jwt = jwtUtil.verifyRefreshToken(refreshToken);
        } catch (Exception e) {
            throw new IllegalArgumentException("refreshToken 无效或已过期");
        }
        OpUser user = opUserMapper.selectById(jwt.getSubject());
        if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
            throw new IllegalArgumentException("用户不存在或已禁用");
        }
        return buildLoginResponse(user);
    }

    @Override
    @Transactional
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) return;
        try {
            DecodedJWT jwt = jwtUtil.verifyAccessToken(accessToken);
            opUserMapper.update(null, new LambdaUpdateWrapper<OpUser>()
                    .eq(OpUser::getId, jwt.getSubject())
                    .set(OpUser::getRefreshToken, null)
                    .set(OpUser::getRefreshTokenExpiresAt, null));
        } catch (Exception ignored) {
        }
    }

    private LoginResponse doLogin(OpUser user, String rawPassword) {
        if (user == null || Boolean.FALSE.equals(user.getEnabled())
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            return LoginResponse.builder().token(null).build();
        }
        return buildLoginResponse(user);
    }

    private LoginResponse buildLoginResponse(OpUser user) {
        opUserMapper.update(null, new LambdaUpdateWrapper<OpUser>()
                .eq(OpUser::getId, user.getId())
                .set(OpUser::getLastLoginAt, LocalDateTime.now()));

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole(), user.getPhone());
        String refreshToken = jwtUtil.generateRefreshToken(
                user.getId(), user.getUsername(), user.getRole(), user.getPhone());

        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtil.getAccessTokenExpiresInSeconds())
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .phone(user.getPhone())
                .build();
    }
}
