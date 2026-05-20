package com.xinya.business.robot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotDeviceStatusDto {
    private String deviceId;
    private String patientId;
    private String onlineStatus;
    private String lastHeartbeatAt;
    private String networkStatus;
    private Integer batteryLevel;
    private String appVersion;
}
