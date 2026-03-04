package com.xinya.dtx.pro.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymptomTrendResponseDto {

    private String questionTitle;

    private List<SymptomTrendPointDto> trend;

    private Double avgScore;

    private Integer peakScore;

    private String peakDate;
}

