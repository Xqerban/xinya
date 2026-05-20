package com.xinya.business.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResultItem {
    private String clientId;
    private String serverId;
    private Integer errorCode;
    private String errorMessage;
}
