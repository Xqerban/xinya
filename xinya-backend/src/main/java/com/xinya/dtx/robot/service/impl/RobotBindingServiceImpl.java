package com.xinya.dtx.robot.service.impl;

import com.xinya.dtx.robot.dto.RobotAuthResponse;
import com.xinya.dtx.robot.dto.RobotBindCodeResponse;
import com.xinya.dtx.robot.dto.RobotBindRequest;
import com.xinya.dtx.robot.dto.RobotUnbindRequest;
import com.xinya.dtx.robot.dto.RobotUnbindResponse;
import com.xinya.dtx.robot.service.RobotBindingService;
import com.xinya.dtx.system.entity.Patient;
import com.xinya.dtx.robot.entity.RobotBindCode;
import com.xinya.dtx.robot.entity.RobotDevice;
import com.xinya.dtx.system.mapper.PatientMapper;
import com.xinya.dtx.robot.mapper.RobotBindCodeMapper;
import com.xinya.dtx.robot.mapper.RobotDeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RobotBindingServiceImpl implements RobotBindingService {

    private static final long DEVICE_TOKEN_EXPIRES_IN_SECONDS = Duration.ofDays(30).getSeconds();

    private final RobotDeviceMapper robotDeviceMapper;
    private final RobotBindCodeMapper robotBindCodeMapper;
    private final PatientMapper patientMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public RobotAuthResponse bind(RobotBindRequest request) {
        LocalDateTime now = LocalDateTime.now();

        // 校验绑定码是否有效
        RobotBindCode bindCode = robotBindCodeMapper
                .findValidCode(request.getPatientId(), request.getBindCode(), now)
                .orElseThrow(() -> new IllegalArgumentException("绑定码无效或已过期"));

        // 校验患者是否存在
        Patient patient = patientMapper.findById(request.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("患者不存在"));

        // 生成设备令牌并保存 hash
        String deviceToken = "device-" + UUID.randomUUID();
        String tokenHash = passwordEncoder.encode(deviceToken);
        LocalDateTime expiresAt = now.plusSeconds(DEVICE_TOKEN_EXPIRES_IN_SECONDS);

        // 绑定或更新设备记录
        RobotDevice device = robotDeviceMapper.findByDeviceId(request.getDeviceId())
                .orElseGet(() -> RobotDevice.builder()
                        .deviceId(request.getDeviceId())
                        .patientId(request.getPatientId())
                        .deviceTokenHash(tokenHash)
                        .tokenExpiresAt(expiresAt)
                        .onlineStatus("ONLINE")
                        .build());

        // 如果是已存在设备，更新绑定信息
        device.setPatientId(request.getPatientId());
        device.setDeviceTokenHash(tokenHash);
        device.setTokenExpiresAt(expiresAt);

        robotDeviceMapper.save(device);

        // 标记绑定码已使用
        robotBindCodeMapper.markUsed(bindCode.getId());

        return RobotAuthResponse.builder()
                .deviceToken(deviceToken)
                .expiresIn(DEVICE_TOKEN_EXPIRES_IN_SECONDS)
                .patientId(patient.getId())
                .patientName(patient.getName())
                .build();
    }

    @Override
    @Transactional
    public RobotUnbindResponse unbind(RobotUnbindRequest request) {
        return robotDeviceMapper.findByDeviceId(request.getDeviceId())
                .map(device -> {
                    // 如果前端传入了 patientId，做一次安全校验
                    if (request.getPatientId() != null
                            && !request.getPatientId().isBlank()
                            && !request.getPatientId().equals(device.getPatientId())) {
                        throw new IllegalArgumentException("患者ID与当前绑定不一致，无法解绑");
                    }

                    // 删除设备记录，视为解绑
                    robotDeviceMapper.delete(device);

                    return RobotUnbindResponse.builder()
                            .deviceId(request.getDeviceId())
                            .unbound(true)
                            .build();
                })
                // 未找到设备，视为已解绑（幂等）
                .orElseGet(() -> RobotUnbindResponse.builder()
                        .deviceId(request.getDeviceId())
                        .unbound(false)
                        .build());
    }

    @Override
    @Transactional
    public RobotBindCodeResponse generateBindCode(String patientId) {
        LocalDateTime now = LocalDateTime.now();

        // 若已存在一条未过期的绑定码，则复用，避免短时间内生成过多绑定码
        var existingOpt = robotBindCodeMapper.findLatestValidByPatientId(patientId, now);
        if (existingOpt.isPresent()) {
            var existing = existingOpt.get();
            long secondsLeft = Duration.between(now, existing.getExpiresAt()).getSeconds();
            if (secondsLeft < 0) {
                secondsLeft = 0;
            }
            return RobotBindCodeResponse.builder()
                    .bindCode(existing.getBindCode())
                    .expiresIn(secondsLeft)
                    .build();
        }

        // 生成新的 6 位数字绑定码
        String bindCode = String.format("%06d", (int) (Math.random() * 1_000_000));
        LocalDateTime expiresAt = now.plusMinutes(5);

        RobotBindCode code = RobotBindCode.builder()
                .patientId(patientId)
                .bindCode(bindCode)
                .createdBy(null) // 后续可接入当前护士用户ID
                .expiresAt(expiresAt)
                .used(false)
                .build();

        robotBindCodeMapper.save(code);

        return RobotBindCodeResponse.builder()
                .bindCode(bindCode)
                .expiresIn(Duration.between(now, expiresAt).getSeconds())
                .build();
    }
}

