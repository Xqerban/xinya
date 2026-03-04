package com.xinya.dtx.robot.controller;

import com.xinya.dtx.common.response.ApiResponse;
import com.xinya.dtx.robot.dto.RobotDataRequest;
import com.xinya.dtx.robot.dto.RobotDataResponse;
import com.xinya.dtx.robot.dto.RobotDeviceStatusDto;
import com.xinya.dtx.robot.dto.RobotHeartbeatRequest;
import com.xinya.dtx.robot.dto.RobotHeartbeatResponse;
import com.xinya.dtx.robot.service.RobotService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/robot")
@RequiredArgsConstructor
public class RobotController {

    private final RobotService robotService;

    /**
     * 10.1 接收机器人推送数据
     */
    @PostMapping("/data")
    public ApiResponse<RobotDataResponse> receiveData(@Valid @RequestBody RobotDataRequest request) {
        try {
            RobotDataResponse resp = robotService.receiveData(request, true);
            return ApiResponse.success(resp);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    /**
     * 10.2 机器人心跳检测
     */
    @PostMapping("/heartbeat")
    public ApiResponse<RobotHeartbeatResponse> heartbeat(@Valid @RequestBody RobotHeartbeatRequest request) {
        RobotHeartbeatResponse resp = robotService.heartbeat(request);
        return ApiResponse.success(resp);
    }

    /**
     * 10.3 查询机器人设备状态
     */
    @GetMapping("/devices")
    public ApiResponse<RobotDeviceStatusDto> getDeviceStatus(
            @RequestParam("patientId") String patientId) {
        RobotDeviceStatusDto dto = robotService.getDeviceStatus(patientId);
        return ApiResponse.success(dto);
    }
}

