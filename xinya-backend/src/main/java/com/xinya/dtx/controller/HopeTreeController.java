package com.xinya.dtx.controller;

import com.xinya.dtx.common.dto.ApiResponse;
import com.xinya.dtx.dto.HopeTreeDto;
import com.xinya.dtx.service.HopeTreeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/hopetree")
@RequiredArgsConstructor
@Tag(name = "希望之树", description = "游戏化生长系统")
public class HopeTreeController {
    
    private final HopeTreeService hopeTreeService;
    
    @GetMapping("/{patientId}")
    @Operation(summary = "获取希望之树当前状态")
    public ApiResponse<HopeTreeDto> getStatus(@PathVariable String patientId) {
        HopeTreeDto status = hopeTreeService.getProgress(patientId);
        return ApiResponse.success(status);
    }
    
    @PostMapping("/grow")
    @Operation(summary = "触发希望之树生长", description = "根据用户行为（打卡、学习、对话）增加经验值")
    public ApiResponse<Map<String, Object>> grow(@RequestBody GrowRequest request) {
        HopeTreeService.GrowResult result = hopeTreeService.grow(
            request.getPatientId(),
            request.getGrowthSource(),
            request.getExpAmount()
        );
        
        return ApiResponse.success(Map.of(
            "success", result.success(),
            "newLevel", result.newLevel(),
            "newExp", result.newExp(),
            "levelUp", result.levelUp()
        ));
    }
    
    @Data
    static class GrowRequest {
        private String patientId;
        private String growthSource;  // "check_in" | "education" | "conversation"
        private Integer expAmount;
    }
}
