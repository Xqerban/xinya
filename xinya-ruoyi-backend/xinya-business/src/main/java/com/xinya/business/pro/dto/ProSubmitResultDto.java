package com.xinya.business.pro.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProSubmitResultDto {
    private boolean success;
    private int psychEnergyDelta;
    private int hopeTreeExpDelta;
    private int totalScore;
    private boolean alertCreated;
    private String message;
}
