package com.xinya.dtx.sync.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinya.dtx.agent.dto.AgentChatRequest;
import com.xinya.dtx.agent.service.AgentService;
import com.xinya.dtx.education.dto.EducationProgressRequest;
import com.xinya.dtx.education.service.EducationService;
import com.xinya.dtx.hopetree.dto.HopeTreeGrowRequest;
import com.xinya.dtx.hopetree.service.HopeTreeService;
import com.xinya.dtx.pro.dto.ProSubmitRequest;
import com.xinya.dtx.pro.service.ProService;
import com.xinya.dtx.robot.dto.RobotDataRequest;
import com.xinya.dtx.robot.service.RobotService;
import com.xinya.dtx.sync.dto.SyncBatchItemRequest;
import com.xinya.dtx.sync.dto.SyncBatchRequest;
import com.xinya.dtx.sync.dto.SyncBatchResponse;
import com.xinya.dtx.sync.dto.SyncResultItem;
import com.xinya.dtx.sync.dto.SyncStatusResponse;
import com.xinya.dtx.sync.service.SyncService;
import com.xinya.dtx.system.entity.SyncItem;
import com.xinya.dtx.system.mapper.SyncItemMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SyncServiceImpl implements SyncService {

    private final SyncItemMapper syncItemMapper;
    private final ProService proService;
    private final AgentService agentService;
    private final HopeTreeService hopeTreeService;
    private final EducationService educationService;
    private final RobotService robotService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public SyncBatchResponse batchSync(SyncBatchRequest request) {
        List<SyncResultItem> succeeded = new ArrayList<>();
        List<SyncResultItem> failed = new ArrayList<>();

        for (SyncBatchItemRequest item : request.getItems()) {
            String clientId = item.getClientId();
            try {
                Optional<SyncItem> existedOpt = syncItemMapper.findByClientId(clientId);
                if (existedOpt.isPresent()) {
                    SyncItem existed = existedOpt.get();
                    if ("success".equals(existed.getStatus())) {
                        succeeded.add(SyncResultItem.builder()
                                .clientId(clientId)
                                .serverId(existed.getServerId())
                                .build());
                    } else if ("failed".equals(existed.getStatus())) {
                        failed.add(SyncResultItem.builder()
                                .clientId(clientId)
                                .errorCode(existed.getErrorCode())
                                .errorMessage(existed.getErrorMessage())
                                .build());
                    }
                    continue;
                }

                SyncItem syncItem = SyncItem.builder()
                        .clientId(clientId)
                        .deviceId(request.getDeviceId())
                        .patientId(request.getPatientId())
                        .itemType(item.getType())
                        .payload(toJson(item.getPayload()))
                        .status("pending")
                        .retryCount(item.getRetryCount() != null ? item.getRetryCount() : 0)
                        .clientCreatedAt(item.getCreatedAt())
                        .build();
                syncItemMapper.save(syncItem);

                // 实际路由处理
                String serverId = routeAndProcess(item);
                syncItemMapper.markSuccess(clientId, serverId, LocalDateTime.now());

                succeeded.add(SyncResultItem.builder()
                        .clientId(clientId)
                        .serverId(serverId)
                        .build());
            } catch (IllegalStateException e) {
                syncItemMapper.markFailed(clientId, 409, e.getMessage(), LocalDateTime.now());
                failed.add(SyncResultItem.builder()
                        .clientId(clientId)
                        .errorCode(409)
                        .errorMessage(e.getMessage())
                        .build());
            } catch (EntityNotFoundException e) {
                syncItemMapper.markFailed(clientId, 404, e.getMessage(), LocalDateTime.now());
                failed.add(SyncResultItem.builder()
                        .clientId(clientId)
                        .errorCode(404)
                        .errorMessage(e.getMessage())
                        .build());
            } catch (Exception e) {
                syncItemMapper.markFailed(clientId, 500, e.getMessage(), LocalDateTime.now());
                failed.add(SyncResultItem.builder()
                        .clientId(clientId)
                        .errorCode(500)
                        .errorMessage(e.getMessage())
                        .build());
            }
        }

        return SyncBatchResponse.builder()
                .totalItems(request.getItems().size())
                .succeeded(succeeded)
                .failed(failed)
                .syncedAt(System.currentTimeMillis())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SyncStatusResponse getStatus(String patientId, String deviceId) {
        var recent = syncItemMapper.findRecentByDevice(deviceId, patientId,
                org.springframework.data.domain.PageRequest.of(0, 1));
        String lastSyncAt = recent.stream()
                .findFirst()
                .map(item -> {
                    LocalDateTime t = item.getProcessedAt() != null ? item.getProcessedAt() : item.getCreatedAt();
                    return t != null ? t.toString() : null;
                })
                .orElse(null);
        long pending = syncItemMapper.countByPatientIdAndStatus(patientId, "pending");
        return SyncStatusResponse.builder()
                .lastSyncAt(lastSyncAt)
                .pendingItemsOnServer(pending)
                .build();
    }

    private String routeAndProcess(SyncBatchItemRequest item) throws JsonProcessingException {
        String type = item.getType();
        JsonNode payload = item.getPayload();
        String serverId = "srv-" + UUID.randomUUID();

        switch (type) {
            case "pro_submit" -> {
                ProSubmitRequest req = objectMapper.treeToValue(payload, ProSubmitRequest.class);
                proService.submit(req);
            }
            case "agent_chat" -> {
                AgentChatRequest req = objectMapper.treeToValue(payload, AgentChatRequest.class);
                agentService.chat(req);
            }
            case "hopetree_grow" -> {
                HopeTreeGrowRequest req = objectMapper.treeToValue(payload, HopeTreeGrowRequest.class);
                hopeTreeService.grow(req);
            }
            case "education_progress" -> {
                EducationProgressRequest req = objectMapper.treeToValue(payload, EducationProgressRequest.class);
                educationService.recordProgress(req);
            }
            case "robot_data" -> {
                RobotDataRequest req = objectMapper.treeToValue(payload, RobotDataRequest.class);
                // 已在 SyncService 记录 SyncItem，这里不再重复记录
                robotService.receiveData(req, false);
            }
            default -> throw new IllegalStateException("不支持的同步类型: " + type);
        }
        return serverId;
    }

    private String toJson(JsonNode node) throws JsonProcessingException {
        return node != null ? objectMapper.writeValueAsString(node) : null;
    }
}

