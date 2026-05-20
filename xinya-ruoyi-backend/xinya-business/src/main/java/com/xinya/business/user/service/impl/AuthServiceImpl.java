package com.xinya.business.user.service.impl;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinya.business.user.dto.LoginResponse;
import com.xinya.business.user.dto.RegisterUserRequest;
import com.xinya.business.user.dto.UserDto;
import com.xinya.business.user.entity.User;
import com.xinya.business.user.mapper.UserMapper;
import com.xinya.business.user.service.AuthService;
import com.xinya.common.security.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Set<String> ALLOWED_ROLES = Set.of("NURSE", "DOCTOR", "ADMIN");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;

    @Override
    @Transactional
    public LoginResponse loginByUsername(String username, String password) {
        User user = userMapper.findByUsername(username);
        return doLogin(user, password);
    }

    @Override
    @Transactional
    public LoginResponse loginByPhone(String phone, String password) {
        User user = userMapper.findByPhone(phone);
        return doLogin(user, password);
    }

    @Override
    @Transactional
    public UserDto register(RegisterUserRequest request) {
        if (!ALLOWED_ROLES.contains(request.getRole())) {
            throw new IllegalArgumentException("不支持的角色类型: " + request.getRole());
        }
        if (userMapper.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (request.getPhone() != null && userMapper.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException("手机号已被占用");
        }

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .role(request.getRole())
                .phone(request.getPhone())
                .enabled(true)
                .build();

        userMapper.insert(user);
        return toUserDto(user);
    }

    private LoginResponse doLogin(User user, String rawPassword) {
        if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
            return LoginResponse.builder().build();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            return LoginResponse.builder().build();
        }
        return buildLoginResponse(user);
    }

    @Override
    @Transactional
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken 不能为空");
        }
        DecodedJWT jwt;
        try {
            jwt = jwtTokenUtil.verifyRefreshToken(refreshToken);
        } catch (Exception e) {
            throw new IllegalArgumentException("refreshToken 无效或已过期");
        }

        User user = userMapper.selectById(jwt.getSubject());
        if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
            throw new IllegalArgumentException("用户不存在或已禁用");
        }
        return buildLoginResponse(user);
    }

    private LoginResponse buildLoginResponse(User user) {
        String accessToken = jwtTokenUtil.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole(), user.getPhone());
        String refreshToken = jwtTokenUtil.generateRefreshToken(
                user.getId(), user.getUsername(), user.getRole(), user.getPhone());

        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenUtil.getAccessTokenExpSeconds())
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
        if (accessToken == null || accessToken.isBlank()) return;
        try {
            DecodedJWT jwt = jwtTokenUtil.verifyAccessToken(accessToken);
            User user = userMapper.selectById(jwt.getSubject());
            if (user != null) {
                user.setRefreshToken(null);
                user.setRefreshTokenExpiresAt(null);
                userMapper.updateById(user);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    @Transactional
    public boolean deactivateCurrentUser(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) return false;
        try {
            DecodedJWT jwt = jwtTokenUtil.verifyAccessToken(accessToken);
            return userMapper.deactivateUser(jwt.getSubject()) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    @Transactional
    public boolean deleteCurrentUser(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) return false;
        try {
            DecodedJWT jwt = jwtTokenUtil.verifyAccessToken(accessToken);
            return userMapper.hardDeleteById(jwt.getSubject()) > 0;
        } catch (Exception e) {
            return false;
        }
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
