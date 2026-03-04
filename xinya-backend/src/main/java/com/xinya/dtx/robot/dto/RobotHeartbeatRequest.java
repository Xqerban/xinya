package com.xinya.dtx.robot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RobotHeartbeatRequest {

    @NotBlank
    private String deviceId;

    private String patientId;

    private String networkStatus; // WIFI | 4G | OFFLINE

    private Integer batteryLevel;

    private String appVersion;
}

