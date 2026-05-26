package com.xinya.ops.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 运维平台用户（ADMIN / NURSE / DOCTOR）
 * 独立存储于 xinya_ops.op_users，不与 xinya_dtx.users 共享
 */
@TableName("op_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpUser {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("username")
    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("display_name")
    private String displayName;

    /** ADMIN | NURSE | DOCTOR */
    @TableField("role")
    private String role;

    @TableField("phone")
    private String phone;

    @TableField("refresh_token")
    private String refreshToken;

    @TableField("refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;

    @Builder.Default
    @TableField("enabled")
    private Boolean enabled = true;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
