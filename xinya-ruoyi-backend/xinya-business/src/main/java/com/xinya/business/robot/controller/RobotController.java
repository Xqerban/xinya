package com.xinya.business.robot.controller;

import com.xinya.business.robot.dto.*;
import com.xinya.business.robot.service.RobotService;
import com.xinya.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "机器人设备")
@RestController
@RequestMapping("/api/robot")
@RequiredArgsConstructor
public class RobotController {

    private final RobotService robotService;

    @Operation(summary = "注册/更新设备")
    @PostMapping("/register")
    public R<RobotDeviceDto> register(@RequestBody RobotRegisterRequest request) {
        return R.ok(robotService.registerOrUpdate(request));
    }

    @Operation(summary = "获取设备信息")
    @GetMapping("/{deviceId}")
    public R<RobotDeviceDto> get(@PathVariable String deviceId) {
        return R.ok(robotService.getByDeviceId(deviceId));
    }

    @Operation(summary = "为患者生成绑定码")
    @PostMapping("/bind-code/{patientId}")
    public R<BindCodeDto> generateBindCode(@PathVariable String patientId) {
        return R.ok(robotService.generateBindCode(patientId));
    }

    @Operation(summary = "绑定患者")
    @PostMapping("/bind")
    public R<RobotDeviceDto> bind(@RequestBody RobotBindRequest request) {
        return R.ok(robotService.bindPatient(request));
    }

    @Operation(summary = "解绑患者")
    @DeleteMapping("/{deviceId}/bind")
    public R<RobotDeviceDto> unbind(@PathVariable String deviceId) {
        return R.ok(robotService.unbindPatient(deviceId));
    }
}
