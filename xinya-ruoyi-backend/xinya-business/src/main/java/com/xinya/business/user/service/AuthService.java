package com.xinya.business.user.service;

import com.xinya.business.user.dto.LoginResponse;
import com.xinya.business.user.dto.RegisterUserRequest;
import com.xinya.business.user.dto.UserDto;

public interface AuthService {
    LoginResponse loginByUsername(String username, String password);
    LoginResponse loginByPhone(String phone, String password);
    UserDto register(RegisterUserRequest request);
    LoginResponse refresh(String refreshToken);
    void logout(String accessToken);
    boolean deactivateCurrentUser(String accessToken);
    boolean deleteCurrentUser(String accessToken);
}
