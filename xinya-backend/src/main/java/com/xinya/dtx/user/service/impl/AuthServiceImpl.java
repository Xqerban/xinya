package com.xinya.dtx.user.service.impl;

import com.xinya.dtx.common.response.LoginResponse;
import com.xinya.dtx.common.response.RegisterUserRequest;
import com.xinya.dtx.common.response.UserDto;
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

    /**
     * 令牌有效期（秒），与 API 文档保持一致 24h
     */
    private static final long DEFAULT_EXPIRES_IN_SECONDS = Duration.ofHours(24).getSeconds();

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

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

        // 登录成功，生成访问令牌和刷新令牌
        String token = "access-" + UUID.randomUUID();
        String refreshToken = "refresh-" + UUID.randomUUID();

        // 记录最后登录时间
        userMapper.updateLastLoginAt(user.getId(), LocalDateTime.now());

        return LoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .expiresIn(DEFAULT_EXPIRES_IN_SECONDS)
                .userId(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .role(user.getRole())
                .displayName(user.getDisplayName())
                .build();
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

