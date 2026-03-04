package com.xinya.dtx.dashboard.dto;

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

    private Long totalPatients;

    private Map<String, Long> patientsByStage;

    private Double avgPsychEnergy;

    private Long lowEnergyCount;

    private Long todayCheckInCount;

    private Double checkInRate;

    private List<SymptomTrendDto> symptomTrends;

    private List<AlertItemDto> alerts;

    private LearningStatsDto learningStats;
}

