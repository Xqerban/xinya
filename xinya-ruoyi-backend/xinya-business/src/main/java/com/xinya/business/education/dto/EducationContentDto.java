package com.xinya.business.education.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EducationContentDto {
    private String id;
    private String title;
    private String category;
    private String stage;
    private String contentType;
    private String contentUrl;
    private String thumbnailUrl;
    private String description;
    private Integer durationMinutes;
    private Integer sortOrder;
}
