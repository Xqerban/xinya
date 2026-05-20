package com.xinya.business.robot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinya.business.robot.dto.*;
import com.xinya.business.robot.entity.RobotBindCode;
import com.xinya.business.robot.entity.RobotDevice;
import com.xinya.business.robot.mapper.RobotBindCodeMapper;
import com.xinya.business.robot.mapper.RobotDeviceMapper;
import com.xinya.business.robot.service.RobotService;
import com.xinya.common.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class RobotServiceImpl implements RobotService {

    private final RobotDeviceMapper robotDeviceMapper;
    private final RobotBindCodeMapper bindCodeMapper;

    @Override
    @Transactional
    public RobotDeviceDto registerOrUpdate(RobotRegisterRequest request) {
        RobotDevice device = robotDeviceMapper.findByDeviceId(request.getDeviceId());
        if (device == null) {
            device = RobotDevice.builder()
                    .deviceId(request.getDeviceId())
                    .appVersion(request.getFirmwareVersion())
                    .onlineStatus("ONLINE")
                    .lastHeartbeatAt(LocalDateTime.now())
                    .build();
            robotDeviceMapper.insert(device);
        } else {
            device.setAppVersion(request.getFirmwareVersion());
            device.setOnlineStatus("ONLINE");
            device.setLastHeartbeatAt(LocalDateTime.now());
            robotDeviceMapper.updateById(device);
        }
        return toDto(device);
    }

    @Override
    public RobotDeviceDto getByDeviceId(String deviceId) {
        RobotDevice device = robotDeviceMapper.findByDeviceId(deviceId);
        if (device == null) throw new ResourceNotFoundException("设备不存在");
        return toDto(device);
    }

    @Override
    @Transactional
    public BindCodeDto generateBindCode(String patientId) {
        String code = String.format("%06d", new Random().nextInt(1000000));
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(10);

        RobotBindCode bindCode = RobotBindCode.builder()
                .patientId(patientId)
                .bindCode(code)
                .expiresAt(expiry)
                .used(false)
                .build();
        bindCodeMapper.insert(bindCode);
        return BindCodeDto.builder()
                .code(code)
                .deviceId(null)
                .expiresAt(expiry.toString())
                .build();
    }

    @Override
    @Transactional
    public RobotDeviceDto bindPatient(RobotBindRequest request) {
        RobotDevice device = robotDeviceMapper.findByDeviceId(request.getDeviceId());
        if (device == null) throw new ResourceNotFoundException("设备不存在");

        LambdaQueryWrapper<RobotBindCode> wrapper = new LambdaQueryWrapper<RobotBindCode>()
                .eq(RobotBindCode::getBindCode, request.getBindCode())
                .eq(RobotBindCode::getPatientId, request.getPatientId())
                .eq(RobotBindCode::getUsed, false)
                .gt(RobotBindCode::getExpiresAt, LocalDateTime.now())
                .orderByDesc(RobotBindCode::getCreatedAt)
                .last("LIMIT 1");
        RobotBindCode code = bindCodeMapper.selectOne(wrapper);
        if (code == null) throw new IllegalArgumentException("绑定码无效或已过期");

        code.setUsed(true);
        bindCodeMapper.updateById(code);
        device.setPatientId(request.getPatientId());
        robotDeviceMapper.updateById(device);
        return toDto(device);
    }

    @Override
    @Transactional
    public RobotDeviceDto unbindPatient(String deviceId) {
        RobotDevice device = robotDeviceMapper.findByDeviceId(deviceId);
        if (device == null) throw new ResourceNotFoundException("设备不存在");
        device.setPatientId(null);
        robotDeviceMapper.updateById(device);
        return toDto(device);
    }

    private RobotDeviceDto toDto(RobotDevice d) {
        return RobotDeviceDto.builder()
                .id(d.getId())
                .deviceId(d.getDeviceId())
                .patientId(d.getPatientId())
                .onlineStatus(d.getOnlineStatus())
                .networkStatus(d.getNetworkStatus())
                .batteryLevel(d.getBatteryLevel())
                .appVersion(d.getAppVersion())
                .lastHeartbeatAt(d.getLastHeartbeatAt() != null ? d.getLastHeartbeatAt().toString() : null)
                .createdAt(d.getCreatedAt() != null ? d.getCreatedAt().toString() : null)
                .build();
    }
}
