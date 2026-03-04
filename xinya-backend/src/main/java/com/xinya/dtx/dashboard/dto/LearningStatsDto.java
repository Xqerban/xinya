package com.xinya.dtx.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningStatsDto {

    private Double avgCompletionRate;

    private Double avgWatchTimeMinutes;

    private Double engagementRate;
}

