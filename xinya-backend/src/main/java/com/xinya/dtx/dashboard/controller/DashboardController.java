package com.xinya.dtx.dashboard.controller;

import com.xinya.dtx.common.response.ApiResponse;
import com.xinya.dtx.dashboard.dto.DashboardDto;
import com.xinya.dtx.dashboard.dto.PatientReportDto;
import com.xinya.dtx.dashboard.dto.PsychDistributionDto;
import com.xinya.dtx.dashboard.dto.SymptomHeatmapDto;
import com.xinya.dtx.dashboard.service.DashboardService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public ApiResponse<DashboardDto> getOverview() {
        DashboardDto dto = dashboardService.getOverview();
        return ApiResponse.success(dto);
    }

    @GetMapping("/psych-distribution")
    public ApiResponse<PsychDistributionDto> getPsychDistribution() {
        PsychDistributionDto dto = dashboardService.getPsychDistribution();
        return ApiResponse.success(dto);
    }

    @GetMapping("/symptom-heatmap")
    public ApiResponse<SymptomHeatmapDto> getSymptomHeatmap(
            @RequestParam(value = "days", required = false) Integer days) {
        SymptomHeatmapDto dto = dashboardService.getSymptomHeatmap(days);
        return ApiResponse.success(dto);
    }

    @GetMapping("/patient-report/{patientId}")
    public ApiResponse<PatientReportDto> getPatientReport(@PathVariable String patientId) {
        try {
            PatientReportDto dto = dashboardService.getPatientReport(patientId);
            return ApiResponse.success(dto);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }
}

