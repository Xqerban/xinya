package com.xinya.dtx.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EducationProgressRequest {

    @NotBlank
    private String patientId;

    @NotBlank
    private String contentId;

    @NotNull
    private Integer watchedSeconds;

    private Boolean completed;
}
