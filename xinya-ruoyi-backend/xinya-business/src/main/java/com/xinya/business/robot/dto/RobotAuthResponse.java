package com.xinya.business.robot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotAuthResponse {
    private String deviceToken;
    private long expiresIn;
    private String patientId;
    private String patientName;
}
