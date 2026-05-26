package com.xinya.ops.auth.controller;

import com.xinya.ops.auth.dto.LoginRequest;
import com.xinya.ops.auth.dto.LoginResponse;
import com.xinya.ops.auth.dto.RefreshTokenRequest;
import com.xinya.ops.auth.service.AuthService;
import com.xinya.ops.common.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse resp = authService.loginByUsername(request.getUsername(), request.getPassword());
        if (resp.getToken() == null) {
            return R.fail(401, "用户名或密码错误");
        }
        return R.ok(resp);
    }

    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            return R.ok(authService.refresh(request.getRefreshToken()));
        } catch (IllegalArgumentException e) {
            return R.fail(401, e.getMessage());
        }
    }

    @PostMapping("/logout")
    public R<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
        }
        authService.logout(token);
        return R.ok();
    }
}
