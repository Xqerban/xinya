package com.xinya.dtx.sync.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SyncBatchItemRequest {

    @NotBlank
    private String clientId;

    @NotBlank
    private String type; // pro_submit | agent_chat | hopetree_grow | education_progress | robot_data

    @NotNull
    private JsonNode payload;

    @NotNull
    private Long createdAt;

    private Integer retryCount;
}

