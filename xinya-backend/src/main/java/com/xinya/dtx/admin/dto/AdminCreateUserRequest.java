package com.xinya.dtx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminCreateUserRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String displayName;

    @NotBlank
    private String role; // NURSE | DOCTOR | ADMIN

    private String phone;
}

