package com.xinya.ops.user.dto;

import lombok.*;

/** 推送到 xinya-ruoyi-backend /internal/users/sync 的用户数据体 */
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
