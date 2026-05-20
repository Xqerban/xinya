package com.xinya.business.robot.dto;

import lombok.Data;

@Data
public class RobotUnbindRequest {
    private String deviceId;
    private String patientId;
}
