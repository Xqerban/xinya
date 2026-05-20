package com.xinya.business.clinical.dto;

import lombok.Data;

@Data
public class UpdateStageRequest {
    private String newStage;
    private String reason;
}
