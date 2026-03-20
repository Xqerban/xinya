package com.xinya.dtx.internal.dto;

import lombok.Data;

@Data
public class SyncCrisisKeywordRequest {
    private String keyword;
    private String crisisLevel;
    private Boolean isActive;
}
