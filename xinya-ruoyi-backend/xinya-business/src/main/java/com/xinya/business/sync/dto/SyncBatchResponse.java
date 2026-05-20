package com.xinya.business.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncBatchResponse {
    private int totalItems;
    private List<SyncResultItem> succeeded;
    private List<SyncResultItem> failed;
    private long syncedAt;
}
