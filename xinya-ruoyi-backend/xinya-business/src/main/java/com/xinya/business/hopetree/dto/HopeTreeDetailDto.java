package com.xinya.business.hopetree.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HopeTreeDetailDto {
    private String patientId;
    private Integer currentLevel;
    private Integer currentExp;
    private Integer nextLevelExp;
    private Integer totalGrowthDays;
    private String lastGrowthDate;
    private String levelName;
    private String levelImageUrl;
}
