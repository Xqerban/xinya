package com.xinya.business.robot.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BindCodeDto {
    private String code;
    private String deviceId;
    private String expiresAt;
}
