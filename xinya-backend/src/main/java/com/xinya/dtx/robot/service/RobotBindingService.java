package com.xinya.dtx.robot.service;

import com.xinya.dtx.robot.dto.RobotAuthResponse;
import com.xinya.dtx.robot.dto.RobotBindCodeResponse;
import com.xinya.dtx.robot.dto.RobotBindRequest;
import com.xinya.dtx.robot.dto.RobotUnbindRequest;
import com.xinya.dtx.robot.dto.RobotUnbindResponse;

/**
 * 机器人与患者绑定 / 解绑服务
 */
public interface RobotBindingService {

    /**
     * 机器人绑定患者
     */
    RobotAuthResponse bind(RobotBindRequest request);

    /**
     * 机器人解绑患者
     */
    RobotUnbindResponse unbind(RobotUnbindRequest request);

    /**
     * 生成机器人绑定码（护士在 PAD 端调用）
     */
    RobotBindCodeResponse generateBindCode(String patientId);
}

