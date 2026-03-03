package com.xinya.dtx.patient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HopeTreeDto {

    private Integer currentLevel;

    private Integer currentExp;

    private Integer nextLevelExp;

    private Integer totalGrowthDays;

    private String levelName;

    private String levelImageUrl;

    private Integer todayExpGained;

    private String lastGrowthTime;
}

