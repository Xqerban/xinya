package com.xinya.business.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDto {
    private long totalPatients;
    private Map<String, Long> patientsByStage;
    private double avgPsychEnergy;
    private long lowEnergyCount;
    private long todayCheckInCount;
    private double checkInRate;
    private List<SymptomTrendDto> symptomTrends;
    private List<AlertItemDto> alerts;
    private LearningStatsDto learningStats;
}
