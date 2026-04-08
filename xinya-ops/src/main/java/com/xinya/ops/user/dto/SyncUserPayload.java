package com.xinya.ops.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 推送到 xinya-backend /internal/users/sync 的用户数据体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncUserPayload {
    private String id;
    private String username;
    private String displayName;
    private String role;
    private String phone;
    private Boolean enabled;
    private String passwordHash;
}
