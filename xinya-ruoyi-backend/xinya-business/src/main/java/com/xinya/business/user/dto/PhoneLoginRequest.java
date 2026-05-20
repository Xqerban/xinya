package com.xinya.business.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PhoneLoginRequest {
    @NotBlank
    private String phone;
    @NotBlank
    private String password;
}
