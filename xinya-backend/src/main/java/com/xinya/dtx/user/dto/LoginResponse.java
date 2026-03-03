package com.xinya.dtx.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private String refreshToken;

    /**
     * 过期时间（秒）
     */
    private long expiresIn;

    private String userId;

    private String username;

    private String phone;

    private String role;

    private String displayName;
}

