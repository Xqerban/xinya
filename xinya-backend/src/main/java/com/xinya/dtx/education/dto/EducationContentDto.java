package com.xinya.dtx.education.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationContentDto {

    private String id;
    private String title;
    private String stage;
    private String category;
    private String description;
    private String contentType;
    private Integer durationSeconds;
    private String thumbnailUrl;
    private String mediaUrl;
    private List<String> tags;
    private Integer sortOrder;
    private Boolean isActive;
}
