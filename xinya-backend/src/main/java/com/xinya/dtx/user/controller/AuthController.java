package com.xinya.dtx.user.controller;

import com.xinya.dtx.common.response.*;
import com.xinya.dtx.user.dto.*;
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
     * 1.0 创建医护/运维用户（注册）
     */
    @PostMapping("/register")
    public ApiResponse<UserDto> register(@RequestBody @Valid RegisterUserRequest request) {
        try {
            UserDto userDto = authService.register(request);
            return ApiResponse.success(userDto);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

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
}

