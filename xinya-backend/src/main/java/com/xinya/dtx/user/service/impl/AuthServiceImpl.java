package com.xinya.dtx.user.service.impl;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.xinya.dtx.common.security.JwtUtil;
import com.xinya.dtx.user.dto.LoginResponse;
import com.xinya.dtx.user.dto.RegisterUserRequest;
import com.xinya.dtx.user.dto.UserDto;
import com.xinya.dtx.user.entity.User;
import com.xinya.dtx.user.mapper.UserMapper;
import com.xinya.dtx.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Set<String> ALLOWED_ROLES = Set.of("NURSE", "DOCTOR", "ADMIN");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public LoginResponse loginByUsername(String username, String password) {
        var user = userMapper.findByUsername(username)
                .orElse(null);
        return doLogin(user, password);
    }

    @Override
    @Transactional
    public LoginResponse loginByPhone(String phone, String password) {
        var user = userMapper.findByPhone(phone)
                .orElse(null);
        return doLogin(user, password);
    }

    @Override
    @Transactional
    public UserDto register(RegisterUserRequest request) {
        // 基本校验
        if (!ALLOWED_ROLES.contains(request.getRole())) {
            throw new IllegalArgumentException("不支持的角色类型: " + request.getRole());
        }
        if (userMapper.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (userMapper.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException("手机号已被占用");
        }

        String userId = UUID.randomUUID().toString();
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .id(userId)
                .username(request.getUsername())
                .passwordHash(encodedPassword)
                .displayName(request.getDisplayName())
                .role(request.getRole())
                .phone(request.getPhone())
                .enabled(true)
                .build();

        userMapper.save(user);

        return toUserDto(user);
    }

    private LoginResponse doLogin(User user, String rawPassword) {
        if (user == null) {
            // 用户不存在
            return LoginResponse.builder()
                    .token(null)
                    .refreshToken(null)
                    .expiresIn(0)
                    .userId(null)
                    .username(null)
                    .phone(null)
                    .role(null)
                    .displayName(null)
                    .build();
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            // 用户被禁用
            return LoginResponse.builder()
                    .token(null)
                    .refreshToken(null)
                    .expiresIn(0)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .phone(user.getPhone())
                    .role(user.getRole())
                    .displayName(user.getDisplayName())
                    .build();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            // 密码错误
            return LoginResponse.builder()
                    .token(null)
                    .refreshToken(null)
                    .expiresIn(0)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .phone(user.getPhone())
                    .role(user.getRole())
                    .displayName(user.getDisplayName())
                    .build();
        }
        // 登录成功，生成新的 access token 和 refresh token
        return buildLoginResponse(user);
    }

    /**
     * 使用 refreshToken 刷新登录态
     */
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

        String userId = jwt.getSubject();

        User user = userMapper.findById(userId)
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .orElseThrow(() -> new IllegalArgumentException("用户不存在或已禁用"));

        // 刷新时同样更新 lastLoginAt，并下发新的 access/refresh token
        return buildLoginResponse(user);
    }

    private LoginResponse buildLoginResponse(User user) {
        LocalDateTime now = LocalDateTime.now();

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole(), user.getPhone());
        String refreshToken = jwtUtil.generateRefreshToken(
                user.getId(), user.getUsername(), user.getRole(), user.getPhone());

        user.setLastLoginAt(now);

        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtil.getAccessTokenExpiresInSeconds())
                .userId(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .role(user.getRole())
                .displayName(user.getDisplayName())
                .build();
    }

    @Override
    @Transactional
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        DecodedJWT jwt;
        try {
            jwt = jwtUtil.verifyAccessToken(accessToken);
        } catch (Exception e) {
            // token 无效时，直接返回即可，保持幂等
            return;
        }
        String userId = jwt.getSubject();
        userMapper.findById(userId).ifPresent(user -> {
            // 对于 JWT accessToken，我们无法“收回”，这里只清空 refreshToken，
            // 让客户端无法再用旧的 refreshToken 获取新的 accessToken。
            user.setRefreshToken(null);
            user.setRefreshTokenExpiresAt(null);
        });
    }

    private UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .phone(user.getPhone())
                .enabled(user.getEnabled())
                .build();
    }
}

