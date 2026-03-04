package com.xinya.dtx.alerts.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAlertRequest {

    @NotBlank
    private String patientId;

    @NotBlank
    private String alertType; // manual

    @NotBlank
    private String level; // info | warning | critical

    @NotBlank
    private String message;
}

