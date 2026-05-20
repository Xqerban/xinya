package com.xinya.business.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NursePushRequest {
    @NotBlank
    private String patientId;
    private String triggerType;
}
