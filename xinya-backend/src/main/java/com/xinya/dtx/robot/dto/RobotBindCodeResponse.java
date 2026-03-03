package com.xinya.dtx.robot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生成机器人绑定码响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotBindCodeResponse {

    private String bindCode;

    /**
     * 绑定码剩余有效期（秒）
     */
    private long expiresIn;
}

