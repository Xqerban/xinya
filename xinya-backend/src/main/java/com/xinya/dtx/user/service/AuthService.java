package com.xinya.dtx.user.service;

import com.xinya.dtx.common.response.LoginResponse;
import com.xinya.dtx.common.response.RegisterUserRequest;
import com.xinya.dtx.common.response.UserDto;

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
}

