package com.xinya.business.hopetree.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HopeTreeGrowthHistoryDto {
    private String id;
    private String patientId;
    private Integer expGained;
    private String growthSource;
    private String notes;
    private Integer levelBefore;
    private Integer levelAfter;
    private String createdAt;
}
