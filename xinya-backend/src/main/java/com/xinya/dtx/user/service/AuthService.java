package com.xinya.dtx.user.service;

import com.xinya.dtx.user.dto.LoginResponse;
import com.xinya.dtx.user.dto.RegisterUserRequest;
import com.xinya.dtx.user.dto.UserDto;

/**
 * 认证与用户管理服务
 */
public interface AuthService {

    /**
     * 用户名登录
     */
    LoginResponse loginByUsername(String username, String password);

    /**
     * 手机号登录
     */
    LoginResponse loginByPhone(String phone, String password);

    /**
     * 创建医护/运维用户（注册）
     */
    UserDto register(RegisterUserRequest request);

    /**
     * 刷新 Token（使用 refreshToken 换取新的 access token）
     */
    LoginResponse refresh(String refreshToken);

    /**
     * 退出登录（服务端维度主要是让 refreshToken 失效）
     */
    void logout(String accessToken);
}

