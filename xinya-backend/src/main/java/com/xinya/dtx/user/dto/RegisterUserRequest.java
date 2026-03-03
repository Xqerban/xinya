package com.xinya.dtx.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建医护/运维用户（注册）请求
 */
@Data
public class RegisterUserRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String displayName;

    /**
     * NURSE / DOCTOR / ADMIN
     */
    @NotBlank
    private String role;

    @NotBlank
    private String phone;
}

