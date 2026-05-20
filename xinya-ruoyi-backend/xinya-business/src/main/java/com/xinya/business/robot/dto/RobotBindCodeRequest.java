package com.xinya.business.robot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RobotBindCodeRequest {
    @NotBlank
    private String patientId;
}
