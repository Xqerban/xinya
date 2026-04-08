package com.xinya.ops.auth.controller;

import com.xinya.ops.auth.dto.LoginRequest;
import com.xinya.ops.auth.dto.LoginResponse;
import com.xinya.ops.auth.dto.RefreshTokenRequest;
import com.xinya.ops.auth.service.AuthService;
import com.xinya.ops.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.loginByUsername(request.getUsername(), request.getPassword());
        if (response.getToken() == null) {
            return ApiResponse.error(401, "用户名或密码错误，或账号已禁用");
        }
        return ApiResponse.success(response);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            return ApiResponse.success(authService.refresh(request.getRefreshToken()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(401, e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authService.logout(authorization.substring(7));
        }
        return ApiResponse.success(null);
    }
}
