package com.xinya.business.education.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateEducationContentRequest {
    @NotBlank
    private String title;
    private String stage;
    @NotBlank
    private String category;
    private String description;
    @NotBlank
    private String contentType;
    private Integer durationSeconds;
    private String thumbnailUrl;
    private String mediaUrl;
    private List<String> tags;
    private Integer sortOrder;
}
