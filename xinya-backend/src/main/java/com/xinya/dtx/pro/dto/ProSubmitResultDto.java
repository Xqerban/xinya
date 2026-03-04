package com.xinya.dtx.pro.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProSubmitResultDto {

    private Boolean success;

    private Integer psychEnergyDelta;

    private Integer hopeTreeExpDelta;

    private Integer totalScore;

    private Boolean alertCreated;

    private String message;
}

