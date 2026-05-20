package com.xinya.business.sync.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class SyncBatchItemRequest {
    private String clientId;
    private String type;
    private JsonNode payload;
    private Integer retryCount;
    private Long createdAt;
}
