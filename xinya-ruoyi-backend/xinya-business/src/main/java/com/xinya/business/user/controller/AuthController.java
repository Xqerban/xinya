package com.xinya.business.user.controller;

import com.xinya.business.user.dto.*;
import com.xinya.business.user.service.AuthService;
import com.xinya.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "医护认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户名登录")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse resp;
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            resp = authService.loginByPhone(request.getPhone(), request.getPassword());
        } else {
            resp = authService.loginByUsername(request.getUsername(), request.getPassword());
        }
        if (resp == null || resp.getToken() == null) {
            return R.fail("用户名或密码错误");
        }
        return R.ok(resp);
    }

    @Operation(summary = "注册医护账号")
    @PostMapping("/register")
    public R<UserDto> register(@Valid @RequestBody RegisterUserRequest request) {
        return R.ok(authService.register(request));
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return R.ok(authService.refresh(request.getRefreshToken()));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractToken(authHeader);
        authService.logout(token);
        return R.ok();
    }

    @Operation(summary = "注销账号")
    @DeleteMapping("/account")
    public R<Void> deleteAccount(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractToken(authHeader);
        authService.deleteCurrentUser(token);
        return R.ok();
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }
}
