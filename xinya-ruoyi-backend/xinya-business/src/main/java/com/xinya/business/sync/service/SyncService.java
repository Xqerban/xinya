package com.xinya.business.sync.service;

import com.xinya.business.sync.dto.*;

public interface SyncService {
    SyncBatchResponse syncBatch(SyncBatchRequest request);
    SyncStatusResponse getSyncStatus(String deviceId);
}
