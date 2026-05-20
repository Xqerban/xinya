package com.xinya.business.education.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EducationProgressRequest {
    @NotBlank
    private String patientId;
    @NotBlank
    private String contentId;
    private Integer watchedSeconds;
    private Boolean completed;
}
