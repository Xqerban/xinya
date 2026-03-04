package com.xinya.dtx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理端修改用户信息请求
 */
@Data
public class AdminUpdateUserRequest {

    /**
     * 展示名
     */
    @NotBlank
    private String displayName;

    /**
     * 角色：NURSE / DOCTOR / ADMIN
     */
    @NotBlank
    private String role;

    /**
     * 手机号，可为空，但若不为空需全局唯一
     */
    private String phone;

    /**
     * 是否启用
     */
    private Boolean enabled;
}

