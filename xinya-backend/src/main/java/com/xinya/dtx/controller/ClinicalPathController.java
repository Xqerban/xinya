package com.xinya.dtx.controller;

import com.xinya.dtx.common.dto.ApiResponse;
import com.xinya.dtx.dto.PatientDto;
import com.xinya.dtx.service.ClinicalPathService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clinical")
@RequiredArgsConstructor
@Tag(name = "临床路径", description = "5阶段临床路径状态机管理")
public class ClinicalPathController {
    
    private final ClinicalPathService clinicalPathService;
    
    @GetMapping("/stage/{patientId}")
    @Operation(summary = "获取患者当前临床阶段")
    public ApiResponse<String> getCurrentStage(@PathVariable String patientId) {
        return clinicalPathService.getCurrentStage(patientId)
            .map(ApiResponse::success)
            .orElse(ApiResponse.error(404, "患者不存在"));
    }
    
    @PostMapping("/transition")
    @Operation(summary = "执行阶段流转", description = "将患者流转到指定的临床阶段")
    public ApiResponse<PatientDto> transitionStage(@RequestBody TransitionRequest request) {
        return clinicalPathService.transitionToStage(request.getPatientId(), request.getTargetStage())
            .map(ApiResponse::success)
            .orElse(ApiResponse.error(400, "阶段流转失败"));
    }
    
    @Data
    static class TransitionRequest {
        private String patientId;
        private String targetStage;
    }
}
