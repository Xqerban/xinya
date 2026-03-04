package com.xinya.dtx.hopetree.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthHistoryItemDto {

    private Long id;

    private String growthSource;

    private String growthSourceName;

    private Integer expAmount;

    private Integer levelBefore;

    private Integer levelAfter;

    private Boolean levelUp;

    private String createdAt;
}
