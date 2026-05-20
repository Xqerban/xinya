package com.xinya.business.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SyncUserRequest {
    private String id;
    @NotBlank
    private String username;
    private String passwordHash;
    @NotBlank
    private String displayName;
    @NotBlank
    private String role;
    private String phone;
    private Boolean enabled;
}
