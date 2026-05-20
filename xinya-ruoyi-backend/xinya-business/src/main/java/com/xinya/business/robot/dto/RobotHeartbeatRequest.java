package com.xinya.business.robot.dto;

import lombok.Data;

@Data
public class RobotHeartbeatRequest {
    private String deviceId;
    private String patientId;
    private String networkStatus;
    private Integer batteryLevel;
    private String appVersion;
}
