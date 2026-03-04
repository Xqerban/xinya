package com.xinya.dtx.sync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SyncBatchRequest {

    @NotBlank
    private String deviceId;

    @NotBlank
    private String patientId;

    @NotEmpty
    private List<SyncBatchItemRequest> items;
}

