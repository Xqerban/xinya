package com.xinya.business.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String phone;
    @NotBlank
    private String password;
}
