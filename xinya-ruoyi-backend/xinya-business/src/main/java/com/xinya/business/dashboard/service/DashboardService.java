package com.xinya.business.dashboard.service;

import com.xinya.business.dashboard.dto.DashboardDto;
import com.xinya.business.dashboard.dto.PsychDistributionDto;

import java.util.Map;

public interface DashboardService {
    DashboardDto getDashboardStats();
    PsychDistributionDto getPsychDistribution();
    Map<String, Object> getPatientReport(String patientId);
}
