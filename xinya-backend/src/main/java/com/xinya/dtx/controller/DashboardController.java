package com.xinya.dtx.controller;

import com.xinya.dtx.common.dto.ApiResponse;
import com.xinya.dtx.dto.DashboardDto;
import com.xinya.dtx.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "数据驾驶舱", description = "医护端数据概览和分析")
public class DashboardController {
    
    private final AnalyticsService analyticsService;
    
    @GetMapping("/overview")
    @Operation(summary = "获取驾驶舱概览数据")
    public ApiResponse<DashboardDto> getOverview() {
        DashboardDto data = analyticsService.getDashboardOverview();
        return ApiResponse.success(data);
    }
}
