package com.xinya.business.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("users")
public class User {

    @TableId(type = IdType.INPUT)
    private String id;

    private String username;
    private String passwordHash;
    private String displayName;
    /** NURSE | DOCTOR | ADMIN */
    private String role;
    private String phone;
    private String refreshToken;
    private LocalDateTime refreshTokenExpiresAt;

    @Builder.Default
    private Boolean enabled = true;

    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
