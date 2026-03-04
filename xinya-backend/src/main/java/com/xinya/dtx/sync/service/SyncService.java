package com.xinya.dtx.sync.service;

import com.xinya.dtx.sync.dto.SyncBatchRequest;
import com.xinya.dtx.sync.dto.SyncBatchResponse;
import com.xinya.dtx.sync.dto.SyncStatusResponse;

public interface SyncService {

    SyncBatchResponse batchSync(SyncBatchRequest request);

    SyncStatusResponse getStatus(String patientId, String deviceId);
}

