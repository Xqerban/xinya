package com.xinya.dtx.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新 Token 请求
 */
@Data
public class RefreshTokenRequest {

    @NotBlank
    private String refreshToken;
}

