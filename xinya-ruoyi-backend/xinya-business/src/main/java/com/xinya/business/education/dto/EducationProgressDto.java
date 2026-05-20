package com.xinya.business.education.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EducationProgressDto {
    private String id;
    private String patientId;
    private String contentId;
    private Boolean completed;
    private String completedAt;
}
