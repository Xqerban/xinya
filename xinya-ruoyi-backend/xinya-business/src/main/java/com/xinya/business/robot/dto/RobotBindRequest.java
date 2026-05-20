package com.xinya.business.robot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RobotBindRequest {
    @NotBlank
    private String deviceId;
    @NotBlank
    private String patientId;
    @NotBlank
    private String bindCode;
}
