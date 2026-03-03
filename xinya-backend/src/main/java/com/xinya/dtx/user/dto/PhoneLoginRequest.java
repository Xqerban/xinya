package com.xinya.dtx.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 手机号密码登录请求
 */
@Data
public class PhoneLoginRequest {

    @NotBlank
    private String phone;

    @NotBlank
    private String password;
}

