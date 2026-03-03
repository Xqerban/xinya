package com.xinya.dtx.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NursePushContentDto {

    private String contentId;

    private String title;

    private String contentType;

    private String thumbnailUrl;

    private Integer durationSeconds;

    private Double relevanceScore;
}

