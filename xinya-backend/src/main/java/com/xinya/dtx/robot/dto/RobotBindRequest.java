package com.xinya.dtx.robot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 机器人绑定患者请求（设备端）
 */
@Data
public class RobotBindRequest {

    @NotBlank
    private String deviceId;

    @NotBlank
    private String patientId;

    /**
     * 护士在 PAD 端生成的 6 位绑定码
     */
    @NotBlank
    private String bindCode;
}

