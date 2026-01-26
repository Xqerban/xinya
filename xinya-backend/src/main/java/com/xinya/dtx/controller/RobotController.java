package com.xinya.dtx.controller;

import com.xinya.dtx.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/robot")
@RequiredArgsConstructor
@Tag(name = "机器人对接", description = "接收机器人端数据")
public class RobotController {
    
    @PostMapping("/data")
    @Operation(summary = "接收机器人推送的数据")
    public ApiResponse<Map<String, Object>> receiveData(@RequestBody RobotDataRequest request) {
        log.info("收到机器人数据: patientId={}, dataType={}", request.getPatientId(), request.getDataType());
        
        // TODO: 处理机器人数据，根据数据类型分发到不同的服务
        
        return ApiResponse.success(Map.of(
            "received", true,
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    @Data
    static class RobotDataRequest {
        private String patientId;
        private String dataType;  // "vital_signs" | "activity" | "voice" | etc.
        private Map<String, Object> payload;
        private Long timestamp;
    }
}
