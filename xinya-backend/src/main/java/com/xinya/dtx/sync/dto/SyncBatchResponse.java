package com.xinya.dtx.sync.dto;

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

    private Integer totalItems;

    private List<SyncResultItem> succeeded;

    private List<SyncResultItem> failed;

    private Long syncedAt;
}

