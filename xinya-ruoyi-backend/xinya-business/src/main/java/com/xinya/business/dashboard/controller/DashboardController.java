package com.xinya.business.dashboard.controller;

import com.xinya.business.dashboard.dto.DashboardDto;
import com.xinya.business.dashboard.dto.PsychDistributionDto;
import com.xinya.business.dashboard.service.DashboardService;
import com.xinya.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "仪表盘统计")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "获取仪表盘统计数据（全量）")
    @GetMapping
    public R<DashboardDto> getDashboard() {
        return R.ok(dashboardService.getDashboardStats());
    }

    @Operation(summary = "获取仪表盘概览")
    @GetMapping("/overview")
    public R<DashboardDto> getOverview() {
        return R.ok(dashboardService.getDashboardStats());
    }

    @Operation(summary = "获取心理能量分布")
    @GetMapping("/psych-distribution")
    public R<PsychDistributionDto> getPsychDistribution() {
        return R.ok(dashboardService.getPsychDistribution());
    }

    @Operation(summary = "获取患者报告")
    @GetMapping("/patient-report/{patientId}")
    public R<Map<String, Object>> getPatientReport(@PathVariable String patientId) {
        return R.ok(dashboardService.getPatientReport(patientId));
    }
}
