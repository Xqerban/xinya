package com.xinya.dtx.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymptomHeatmapDto {

    private List<String> dates;

    private List<SymptomSeriesDto> symptoms;
}

