package com.xinya.dtx.robot.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinya.dtx.patient.mapper.PatientMapper;
import com.xinya.dtx.robot.dto.RobotDataRequest;
import com.xinya.dtx.robot.dto.RobotDataResponse;
import com.xinya.dtx.robot.dto.RobotDeviceStatusDto;
import com.xinya.dtx.robot.dto.RobotHeartbeatRequest;
import com.xinya.dtx.robot.dto.RobotHeartbeatResponse;
import com.xinya.dtx.robot.entity.RobotDevice;
import com.xinya.dtx.robot.mapper.RobotDeviceMapper;
import com.xinya.dtx.sync.entity.SyncItem;
import com.xinya.dtx.sync.mapper.SyncItemMapper;
import com.xinya.dtx.robot.service.RobotService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RobotServiceImpl implements RobotService {

    private final RobotDeviceMapper robotDeviceMapper;
    private final PatientMapper patientMapper;
    private final SyncItemMapper syncItemMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public RobotDataResponse receiveData(RobotDataRequest request, boolean recordSync) {
        // 校验绑定关系是否存在
        if (!patientMapper.existsById(request.getPatientId())) {
            throw new EntityNotFoundException("患者不存在");
        }
        if (recordSync) {
            try {
                SyncItem item = SyncItem.builder()
                        .clientId("robot-" + request.getDeviceId() + "-" + System.currentTimeMillis())
                        .deviceId(request.getDeviceId())
                        .patientId(request.getPatientId())
                        .itemType("robot_data")
                        .payload(objectMapper.writeValueAsString(request))
                        .status("success")
                        .serverId("realtime-" + System.currentTimeMillis())
                        .clientCreatedAt(request.getTimestamp())
                        .processedAt(LocalDateTime.now())
                        .build();
                syncItemMapper.save(item);
            } catch (JsonProcessingException ignored) {
            }
        }
        // 这里可以根据 dataType 进一步分发到不同的处理器，目前先简单确认接收成功
        return RobotDataResponse.builder()
                .received(true)
                .timestamp(request.getTimestamp())
                .build();
    }

    @Override
    @Transactional
    public RobotHeartbeatResponse heartbeat(RobotHeartbeatRequest request) {
        LocalDateTime now = LocalDateTime.now();
        // 若不存在设备记录，则创建一条（便于 10.3 查询状态）
        RobotDevice device = robotDeviceMapper.findByDeviceId(request.getDeviceId())
                .orElseGet(() -> RobotDevice.builder()
                        .deviceId(request.getDeviceId())
                        .patientId(request.getPatientId())
                        .onlineStatus("ONLINE")
                        .build());

        if (request.getPatientId() != null && !request.getPatientId().isBlank()) {
            device.setPatientId(request.getPatientId());
        }
        device.setOnlineStatus("ONLINE");
        device.setLastHeartbeatAt(now);
        device.setNetworkStatus(request.getNetworkStatus());
        device.setBatteryLevel(request.getBatteryLevel());
        device.setAppVersion(request.getAppVersion());
        robotDeviceMapper.save(device);

        return RobotHeartbeatResponse.builder()
                .serverTime(System.currentTimeMillis())
                .pendingPushMessages(0)
                .build();
    }

    @Override
    @Transactional
    public RobotDeviceStatusDto getDeviceStatus(String patientId) {
        return robotDeviceMapper.findByPatientId(patientId)
                .map(this::toDto)
                .orElse(null);
    }

    private RobotDeviceStatusDto toDto(RobotDevice d) {
        return RobotDeviceStatusDto.builder()
                .deviceId(d.getDeviceId())
                .patientId(d.getPatientId())
                .onlineStatus(d.getOnlineStatus())
                .lastHeartbeatAt(d.getLastHeartbeatAt() != null ? d.getLastHeartbeatAt().toString() : null)
                .networkStatus(d.getNetworkStatus())
                .batteryLevel(d.getBatteryLevel())
                .appVersion(d.getAppVersion())
                .build();
    }
}

