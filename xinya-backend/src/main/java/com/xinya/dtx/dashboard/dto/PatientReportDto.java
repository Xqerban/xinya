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
public class PatientReportDto {

    private String patientId;
    private String patientName;
    private String admissionDate;
    private String currentStage;
    private Integer totalDays;
    private Integer checkInDays;
    private Double checkInRate;

    private Map<String, Object> psychEnergyProgress;

    private Map<String, Object> hopeTreeProgress;

    private Map<String, Object> educationProgress;

    private List<Map<String, Object>> symptomSummary;

    private String generatedAt;
}

