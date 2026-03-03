package com.xinya.dtx.common.response;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户名密码登录请求
 */
@Data
public class LoginRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}

