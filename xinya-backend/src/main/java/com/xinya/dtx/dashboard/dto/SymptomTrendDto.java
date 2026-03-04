package com.xinya.dtx.dashboard.dto;

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

    private Integer lastWeek;

    private Integer thisWeek;

    private Double changePercent;

    private String trend; // UP / DOWN / FLAT
}

