package com.xinya.dtx.patient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LatestProRecordDto {

    private String recordDate;

    private Integer totalScore;

    private Integer answerCount;
}

