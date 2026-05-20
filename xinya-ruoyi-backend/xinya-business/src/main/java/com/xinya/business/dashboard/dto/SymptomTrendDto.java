package com.xinya.business.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymptomTrendDto {
    private String symptom;
    private int lastWeek;
    private int thisWeek;
    private double changePercent;
    private String trend;
}
