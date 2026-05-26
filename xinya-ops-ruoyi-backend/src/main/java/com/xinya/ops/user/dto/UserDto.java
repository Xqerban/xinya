package com.xinya.ops.user.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private String id;
    private String username;
    private String displayName;
    private String role;
    private String phone;
    private Boolean enabled;
    private String createdAt;
}
