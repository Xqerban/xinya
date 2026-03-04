package com.xinya.dtx.robot.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RobotDataRequest {

    @NotBlank
    private String patientId;

    @NotBlank
    private String deviceId;

    @NotBlank
    private String dataType; // vital_signs | activity | voice | environment

    @NotNull
    private JsonNode payload;

    @NotNull
    private Long timestamp;
}

