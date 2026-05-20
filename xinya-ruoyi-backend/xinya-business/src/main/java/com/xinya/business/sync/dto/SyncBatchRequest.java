package com.xinya.business.sync.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SyncBatchRequest {
    @NotBlank
    private String deviceId;
    @NotBlank
    private String patientId;
    private List<SyncBatchItemRequest> items;
}
