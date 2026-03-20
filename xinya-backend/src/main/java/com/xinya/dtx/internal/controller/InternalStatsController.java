package com.xinya.dtx.internal.controller;

import com.xinya.dtx.alerts.mapper.AlertMapper;
import com.xinya.dtx.common.response.ApiResponse;
import com.xinya.dtx.dashboard.dto.DashboardDto;
import com.xinya.dtx.dashboard.dto.PsychDistributionDto;
import com.xinya.dtx.dashboard.dto.SymptomHeatmapDto;
import com.xinya.dtx.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部统计数据接口（仅供 xinya-ops 调用，需携带 X-Internal-Key）
 * 复用 DashboardService 对 clinical 数据进行聚合，供运维端读取
 */
@RestController
@RequestMapping("/internal/stats")
@RequiredArgsConstructor
public class InternalStatsController {

    private final DashboardService dashboardService;
    private final AlertMapper alertMapper;

    /**
     * 总览数据：患者数量、打卡率、平均心理能量、症状趋势、最新预警
     */
    @GetMapping("/overview")
    public ApiResponse<DashboardDto> getOverview() {
        return ApiResponse.success(dashboardService.getOverview());
    }

    /**
     * 心理状态分布（健康/轻度/预警三档分布）
     */
    @GetMapping("/psych-distribution")
    public ApiResponse<PsychDistributionDto> getPsychDistribution() {
        return ApiResponse.success(dashboardService.getPsychDistribution());
    }

    /**
     * 症状热力图（近 N 天各症状得分矩阵）
     */
    @GetMapping("/symptom-heatmap")
    public ApiResponse<SymptomHeatmapDto> getSymptomHeatmap(
            @RequestParam(value = "days", required = false) Integer days) {
        return ApiResponse.success(dashboardService.getSymptomHeatmap(days));
    }
}
