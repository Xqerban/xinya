package com.xinya.dtx.clinical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalStageInfoDto {

    private String stage;

    private String stageName;

    private Integer stageOrder;

    private String stageStartDate;

    private Integer daysInStage;
}

