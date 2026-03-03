package com.xinya.dtx.robot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 机器人绑定成功后的鉴权响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotAuthResponse {

    /**
     * 设备访问令牌（机器人后续请求使用）
     */
    private String deviceToken;

    /**
     * 令牌过期时间（秒），默认 30 天
     */
    private long expiresIn;

    private String patientId;

    private String patientName;
}

