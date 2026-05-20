package com.xinya.business.sync.controller;

import com.xinya.business.sync.dto.*;
import com.xinya.business.sync.service.SyncService;
import com.xinya.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "数据同步")
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @Operation(summary = "批量同步数据")
    @PostMapping("/batch")
    public R<SyncBatchResponse> syncBatch(@RequestBody SyncBatchRequest request) {
        return R.ok(syncService.syncBatch(request));
    }

    @Operation(summary = "获取同步状态")
    @GetMapping("/status/{deviceId}")
    public R<SyncStatusResponse> getStatus(@PathVariable String deviceId) {
        return R.ok(syncService.getSyncStatus(deviceId));
    }
}
