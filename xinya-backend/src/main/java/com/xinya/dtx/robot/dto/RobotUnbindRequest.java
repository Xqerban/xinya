package com.xinya.dtx.robot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 机器人解绑患者请求
 */
@Data
public class RobotUnbindRequest {

    @NotBlank
    private String deviceId;

    /**
     * 当前绑定的患者 ID，用于安全校验（可选）
     */
    private String patientId;
}

