package com.xinya.dtx.clinical.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StageTransitionRequest {

    @NotBlank
    private String patientId;

    @NotBlank
    private String targetStage;

    private String operatorNote;
}

