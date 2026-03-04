package com.xinya.dtx.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PsychDistributionDto {

    private Bucket healthy;

    private Bucket mild;

    private Bucket warning;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bucket {
        private Long count;
        private String range;
        private Double percent;
    }
}

