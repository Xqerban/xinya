package com.xinya.dtx.dashboard.service;

import com.xinya.dtx.dashboard.dto.DashboardDto;
import com.xinya.dtx.dashboard.dto.PatientReportDto;
import com.xinya.dtx.dashboard.dto.PsychDistributionDto;
import com.xinya.dtx.dashboard.dto.SymptomHeatmapDto;

public interface DashboardService {

    DashboardDto getOverview();

    PsychDistributionDto getPsychDistribution();

    SymptomHeatmapDto getSymptomHeatmap(Integer days);

    PatientReportDto getPatientReport(String patientId);
}

