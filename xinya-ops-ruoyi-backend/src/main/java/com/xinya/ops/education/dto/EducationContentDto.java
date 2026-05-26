package com.xinya.ops.education.dto;

import lombok.*;

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
    private Boolean syncedToClinical;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
}
