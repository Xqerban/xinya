package com.xinya.business.robot.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RobotDeviceDto {
    private Long id;
    private String deviceId;
    private String patientId;
    private String onlineStatus;
    private String networkStatus;
    private Integer batteryLevel;
    private String appVersion;
    private String lastHeartbeatAt;
    private String createdAt;
}
