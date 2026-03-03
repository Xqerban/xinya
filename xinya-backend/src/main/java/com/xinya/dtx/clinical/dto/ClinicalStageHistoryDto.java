package com.xinya.dtx.clinical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalStageHistoryDto {

    private Long id;

    private String fromStage;

    private String toStage;

    private String transitionDate;

    private String operatorId;

    private String operatorNote;

    private Integer daysInStage;
}

