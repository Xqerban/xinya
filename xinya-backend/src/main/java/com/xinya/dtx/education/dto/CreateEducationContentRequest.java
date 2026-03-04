package com.xinya.dtx.education.dto;

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
    private String contentType; // video | article

    private Integer durationSeconds;
    private String thumbnailUrl;
    private String mediaUrl;
    private List<String> tags;
    private Integer sortOrder;
}
