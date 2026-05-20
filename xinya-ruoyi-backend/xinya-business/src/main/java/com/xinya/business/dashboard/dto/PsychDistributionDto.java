package com.xinya.business.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PsychDistributionDto {

    private Band healthy;
    private Band mild;
    private Band warning;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Band {
        private long count;
        private String range;
        private double percent;
    }
}
