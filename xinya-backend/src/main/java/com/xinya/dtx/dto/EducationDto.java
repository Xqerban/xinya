package com.xinya.dtx.dto;

import com.xinya.dtx.entity.EducationContent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationDto {
    private String id;
    private String title;
    private String category;
    private String description;
    private String contentType;
    private Integer durationSeconds;
    private String thumbnailUrl;
    private String mediaUrl;
    private List<String> tags;
    
    public static EducationDto fromEntity(EducationContent content) {
        return EducationDto.builder()
            .id(content.getId())
            .title(content.getTitle())
            .category(content.getCategory())
            .description(content.getDescription())
            .contentType(content.getContentType())
            .durationSeconds(content.getDurationSeconds())
            .thumbnailUrl(content.getThumbnailUrl())
            .mediaUrl(content.getMediaUrl())
            .tags(content.getTags() != null ? 
                  Arrays.asList(content.getTags().split(",")) : List.of())
            .build();
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class EducationListResponse {
    private List<EducationDto> contents;
    private Integer total;
}
