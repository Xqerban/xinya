package com.xinya.dtx.sync.controller;

import com.xinya.dtx.common.response.ApiResponse;
import com.xinya.dtx.sync.dto.SyncBatchRequest;
import com.xinya.dtx.sync.dto.SyncBatchResponse;
import com.xinya.dtx.sync.dto.SyncStatusResponse;
import com.xinya.dtx.sync.service.SyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    /**
     * 11.1 批量同步上传
     */
    @PostMapping("/batch")
    public ApiResponse<SyncBatchResponse> batch(@Valid @RequestBody SyncBatchRequest request) {
        SyncBatchResponse resp = syncService.batchSync(request);
        return ApiResponse.success(resp);
    }

    /**
     * 11.2 查询同步状态
     */
    @GetMapping("/status")
    public ApiResponse<SyncStatusResponse> status(@RequestParam("patientId") String patientId,
                                                  @RequestParam("deviceId") String deviceId) {
        SyncStatusResponse resp = syncService.getStatus(patientId, deviceId);
        return ApiResponse.success(resp);
    }
}

