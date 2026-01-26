package com.xinya.dtx.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 数据驾驶舱DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDto {
    
    private Integer totalPatients;
    private Map<String, Integer> patientsByStage;
    private Double avgPsychEnergy;
    private List<SymptomTrend> symptomTrends;
    private List<AlertItem> alerts;
    private LearningStats learningStats;
    
    public static DashboardDto mockData() {
        return DashboardDto.builder()
            .totalPatients(15)
            .patientsByStage(Map.of(
                "ADMISSION", 3,
                "PRETREATMENT", 4,
                "TRANSPLANT", 2,
                "REBUILD", 5,
                "DISCHARGE", 1
            ))
            .avgPsychEnergy(68.5)
            .symptomTrends(List.of(
                new SymptomTrend("恶心", 12, 8, -33.3),
                new SymptomTrend("疲劳", 10, 11, 10.0),
                new SymptomTrend("失眠", 6, 4, -33.3)
            ))
            .alerts(List.of(
                new AlertItem("warning", "患者张某心理能量持续下降", "P001"),
                new AlertItem("info", "3位患者即将进入重建期", null)
            ))
            .learningStats(new LearningStats(85.5, 4.2, 92.0))
            .build();
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class SymptomTrend {
    private String symptom;
    private Integer lastWeek;
    private Integer thisWeek;
    private Double changePercent;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class AlertItem {
    private String level;  // "warning" | "error" | "info"
    private String message;
    private String patientId;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class LearningStats {
    private Double avgCompletionRate;
    private Double avgWatchTime;
    private Double engagementRate;
}
