package com.xinya.dtx.robot.service;

import com.xinya.dtx.robot.dto.RobotDataRequest;
import com.xinya.dtx.robot.dto.RobotDataResponse;
import com.xinya.dtx.robot.dto.RobotDeviceStatusDto;
import com.xinya.dtx.robot.dto.RobotHeartbeatRequest;
import com.xinya.dtx.robot.dto.RobotHeartbeatResponse;

public interface RobotService {

    /**
     * 接收机器人实时数据。
     * @param request 数据请求
     * @param recordSync 是否写入 sync_items 作为同步记录（离线 batch 已记录时应为 false）
     */
    RobotDataResponse receiveData(RobotDataRequest request, boolean recordSync);

    RobotHeartbeatResponse heartbeat(RobotHeartbeatRequest request);

    RobotDeviceStatusDto getDeviceStatus(String patientId);
}

