package com.xinya.ops.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @NotBlank(message = "显示名称不能为空")
    private String displayName;

    @NotBlank(message = "角色不能为空")
    private String role;

    private String phone;
    private Boolean enabled;

    /** 不为空则重置密码 */
    private String newPassword;
}
