package com.xinya.business.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningStatsDto {
    private double avgCompletionRate;
    private double avgWatchTimeMinutes;
    private double engagementRate;
}
