package com.xinya.business.sync.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinya.business.sync.dto.*;
import com.xinya.business.sync.entity.SyncItem;
import com.xinya.business.sync.mapper.SyncItemMapper;
import com.xinya.business.sync.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SyncServiceImpl implements SyncService {

    private final SyncItemMapper syncItemMapper;

    @Override
    @Transactional
    public SyncBatchResponse syncBatch(SyncBatchRequest request) {
        List<SyncResultItem> succeeded = new ArrayList<>();
        List<SyncResultItem> failed = new ArrayList<>();

        if (request.getItems() != null) {
            for (SyncBatchItemRequest item : request.getItems()) {
                try {
                    LambdaQueryWrapper<SyncItem> wrapper = new LambdaQueryWrapper<SyncItem>()
                            .eq(SyncItem::getPatientId, request.getPatientId())
                            .eq(SyncItem::getClientId, item.getClientId())
                            .last("LIMIT 1");
                    SyncItem existing = syncItemMapper.selectOne(wrapper);
                    if (existing == null) {
                        SyncItem si = SyncItem.builder()
                                .clientId(item.getClientId())
                                .deviceId(request.getDeviceId())
                                .patientId(request.getPatientId())
                                .itemType(item.getType())
                                .payload(item.getPayload() != null ? item.getPayload().toString() : null)
                                .status("success")
                                .clientCreatedAt(item.getCreatedAt())
                                .retryCount(item.getRetryCount() != null ? item.getRetryCount() : 0)
                                .build();
                        syncItemMapper.insert(si);
                        succeeded.add(new SyncResultItem(item.getClientId(),
                                si.getId() != null ? si.getId().toString() : null, null, null));
                    } else {
                        existing.setStatus("success");
                        existing.setPayload(item.getPayload() != null ? item.getPayload().toString() : null);
                        existing.setProcessedAt(LocalDateTime.now());
                        syncItemMapper.updateById(existing);
                        succeeded.add(new SyncResultItem(item.getClientId(),
                                existing.getId().toString(), null, null));
                    }
                } catch (Exception e) {
                    failed.add(new SyncResultItem(item.getClientId(), null, 500, e.getMessage()));
                }
            }
        }
        return SyncBatchResponse.builder()
                .totalItems(succeeded.size() + failed.size())
                .succeeded(succeeded)
                .failed(failed)
                .syncedAt(System.currentTimeMillis())
                .build();
    }

    @Override
    public SyncStatusResponse getSyncStatus(String deviceId) {
        LambdaQueryWrapper<SyncItem> wrapper = new LambdaQueryWrapper<SyncItem>()
                .eq(SyncItem::getDeviceId, deviceId)
                .eq(SyncItem::getStatus, "pending");
        long pendingCount = syncItemMapper.selectCount(wrapper);
        return SyncStatusResponse.builder()
                .lastSyncAt(LocalDateTime.now().toString())
                .pendingItemsOnServer(pendingCount)
                .build();
    }
}
