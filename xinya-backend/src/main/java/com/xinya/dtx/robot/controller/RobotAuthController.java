package com.xinya.dtx.robot.controller;

import com.xinya.dtx.common.response.*;
import com.xinya.dtx.robot.dto.RobotAuthResponse;
import com.xinya.dtx.robot.dto.RobotBindCodeRequest;
import com.xinya.dtx.robot.dto.RobotBindCodeResponse;
import com.xinya.dtx.robot.dto.RobotBindRequest;
import com.xinya.dtx.robot.dto.RobotUnbindRequest;
import com.xinya.dtx.robot.dto.RobotUnbindResponse;
import com.xinya.dtx.robot.service.RobotBindingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 机器人绑定 / 解绑相关接口
 *
 * 对应 API 文档中的：
 * - 1.3 机器人端鉴权（患者绑定） POST /api/auth/robot/bind
 * - 1.4 生成机器人绑定码 POST /api/auth/robot/bind-code
 * - 1.5 机器人解绑患者 POST /api/auth/robot/unbind
 */
@RestController
@RequestMapping("/api/auth/robot")
@RequiredArgsConstructor
public class RobotAuthController {

    private final RobotBindingService robotBindingService;

    /**
     * 1.3 机器人端鉴权（患者绑定）
     */
    @PostMapping("/bind")
    public ApiResponse<RobotAuthResponse> bind(@RequestBody @Valid RobotBindRequest request) {
        try {
            RobotAuthResponse response = robotBindingService.bind(request);
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 1.5 机器人解绑患者
     */
    @PostMapping("/unbind")
    public ApiResponse<RobotUnbindResponse> unbind(@RequestBody @Valid RobotUnbindRequest request) {
        try {
            RobotUnbindResponse response = robotBindingService.unbind(request);
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 1.4 生成机器人绑定码（PAD端护士调用）
     */
    @PostMapping("/bind-code")
    public ApiResponse<RobotBindCodeResponse> generateBindCode(@RequestBody @Valid RobotBindCodeRequest request) {
        try {
            RobotBindCodeResponse response = robotBindingService.generateBindCode(request.getPatientId());
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}

