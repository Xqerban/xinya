package com.xinya.business.robot.dto;

import lombok.Data;

@Data
public class RobotDataRequest {
    private String deviceId;
    private String patientId;
    private String dataType;
    private Object data;
    private Long timestamp;
}
