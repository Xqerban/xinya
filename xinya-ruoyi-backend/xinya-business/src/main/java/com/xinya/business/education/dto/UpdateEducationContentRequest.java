package com.xinya.business.education.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateEducationContentRequest {
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
