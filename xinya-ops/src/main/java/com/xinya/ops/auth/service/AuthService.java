package com.xinya.ops.auth.service;

import com.xinya.ops.auth.dto.LoginResponse;

public interface AuthService {

    LoginResponse loginByUsername(String username, String password);

    LoginResponse refresh(String refreshToken);

    void logout(String accessToken);
}