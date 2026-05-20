package com.xinya.business.alerts.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAlertRequest {
    @NotBlank
    private String patientId;
    @NotBlank
    private String alertType;
    @NotBlank
    private String level;
    @NotBlank
    private String message;
}
