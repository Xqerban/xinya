package com.xinya.dtx.robot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 机器人解绑结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotUnbindResponse {

    private String deviceId;

    /**
     * 是否实际执行了解绑（设备存在且已删除绑定记录）
     */
    private boolean unbound;
}

