package com.xinya.dtx.robot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 生成机器人绑定码请求
 */
@Data
public class RobotBindCodeRequest {

    @NotBlank
    private String patientId;
}

