package com.xinya.ops.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 运维平台用户（ADMIN 运维员 / NURSE 护士 / DOCTOR 医生）
 * 独立存储于 xinya_ops.op_users，不与 xinya_dtx.users 共享
 * 创建/更新医护用户时会同步推送到 xinya-backend
 */
@Entity
@Table(name = "op_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpUser {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "username", length = 100, nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    /** ADMIN | NURSE | DOCTOR */
    @Column(name = "role", length = 20, nullable = false)
    private String role;

    @Column(name = "phone", length = 20, unique = true)
    private String phone;

    @Column(name = "refresh_token", length = 255)
    private String refreshToken;

    @Column(name = "refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
