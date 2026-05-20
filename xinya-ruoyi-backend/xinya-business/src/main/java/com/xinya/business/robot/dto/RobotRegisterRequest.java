package com.xinya.business.robot.dto;

import lombok.Data;

@Data
public class RobotRegisterRequest {
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String firmwareVersion;
}
