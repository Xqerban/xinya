package com.xinya.dtx.user.controller;

import com.xinya.dtx.common.response.*;
import com.xinya.dtx.user.dto.LoginRequest;
import com.xinya.dtx.user.dto.LoginResponse;
import com.xinya.dtx.user.dto.PhoneLoginRequest;
import com.xinya.dtx.user.dto.RefreshTokenRequest;
import com.xinya.dtx.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证与权限相关接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 1.1 用户名登录
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResponse response = authService.loginByUsername(request.getUsername(), request.getPassword());
        if (response.getToken() == null) {
            // 登录失败：用户名不存在 / 密码错误 / 用户被禁用
            return ApiResponse.error(401, "用户名或密码错误，或账号已禁用");
        }
        return ApiResponse.success(response);
    }

    /**
     * 1.2 手机号登录
     */
    @PostMapping("/login/phone")
    public ApiResponse<LoginResponse> loginByPhone(@RequestBody @Valid PhoneLoginRequest request) {
        LoginResponse response = authService.loginByPhone(request.getPhone(), request.getPassword());
        if (response.getToken() == null) {
            return ApiResponse.error(401, "手机号或密码错误，或账号已禁用");
        }
        return ApiResponse.success(response);
    }

    /**
     * 1.6 刷新 Token
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestBody @Valid RefreshTokenRequest request) {
        try {
            LoginResponse response = authService.refresh(request.getRefreshToken());
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(401, "refreshToken 无效或已过期");
        }
    }

    /**
     * 1.7 退出登录
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String accessToken = extractToken(authorization);
        authService.logout(accessToken);
        return ApiResponse.success(null);
    }

    /**
     * 用户自助注销账号（逻辑停用）
     */
    @PostMapping("/deactivate")
    public ApiResponse<Void> deactivate(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String accessToken = extractToken(authorization);
        boolean ok = authService.deactivateCurrentUser(accessToken);
        if (!ok) {
            return ApiResponse.error(401, "注销失败：token 无效或用户不存在");
        }
        return ApiResponse.success("账号已注销", null);
    }

    /**
     * 用户自助删除账号（物理删除）
     */
    @DeleteMapping("/account")
    public ApiResponse<Void> deleteAccount(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String accessToken = extractToken(authorization);
        boolean ok = authService.deleteCurrentUser(accessToken);
        if (!ok) {
            return ApiResponse.error(401, "删除失败：token 无效或用户不存在");
        }
        return ApiResponse.success("账号已删除", null);
    }

    private String extractToken(String authorization) {
        // Authorization: Bearer <token>
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}

