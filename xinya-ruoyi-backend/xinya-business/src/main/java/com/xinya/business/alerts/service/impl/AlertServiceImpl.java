package com.xinya.business.alerts.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.business.alerts.dto.*;
import com.xinya.business.alerts.entity.Alert;
import com.xinya.business.alerts.mapper.AlertMapper;
import com.xinya.business.alerts.service.AlertService;
import com.xinya.common.core.domain.PageResult;
import com.xinya.common.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final AlertMapper alertMapper;

    @Override
    public PageResult<AlertDto> listAlerts(Integer page, Integer pageSize, String patientId, Boolean resolved) {
        int pageIndex = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        Page<Alert> p = new Page<>(pageIndex, size);
        alertMapper.pageAlerts(p,
                (patientId != null && !patientId.isBlank()) ? patientId : null,
                resolved);
        return PageResult.<AlertDto>builder()
                .list(p.getRecords().stream().map(this::toDto).collect(Collectors.toList()))
                .total(p.getTotal())
                .page(pageIndex)
                .pageSize(size)
                .build();
    }

    @Override
    public AlertDto getAlertById(String id) {
        Alert alert = alertMapper.selectById(id);
        if (alert == null) throw new ResourceNotFoundException("警告：资源不存在");
        return toDto(alert);
    }

    @Override
    @Transactional
    public AlertDto resolveAlert(String id, ResolveAlertRequest request) {
        Alert alert = alertMapper.selectById(id);
        if (alert == null) throw new ResourceNotFoundException("警告：资源不存在");
        alert.setResolved(true);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolvedNote(request.getResolvedNote());
        alertMapper.updateById(alert);
        return toDto(alert);
    }

    @Override
    @Transactional
    public AlertDto createAlert(CreateAlertRequest request) {
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .patientId(request.getPatientId())
                .alertType(request.getAlertType())
                .level(request.getLevel())
                .message(request.getMessage())
                .resolved(false)
                .build();
        alertMapper.insert(alert);
        return toDto(alert);
    }

    @Override
    public long countUnresolvedByPatient(String patientId) {
        return alertMapper.countByPatientIdAndResolvedFalse(patientId);
    }

    private AlertDto toDto(Alert alert) {
        return AlertDto.builder()
                .id(alert.getId())
                .patientId(alert.getPatientId())
                .patientName(alert.getPatientName())
                .alertType(alert.getAlertType())
                .level(alert.getLevel())
                .message(alert.getMessage())
                .triggerMessage(alert.getTriggerMessage())
                .resolved(Boolean.TRUE.equals(alert.getResolved()))
                .resolvedBy(alert.getResolvedBy())
                .resolvedNote(alert.getResolvedNote())
                .resolvedAt(alert.getResolvedAt() != null ? alert.getResolvedAt().toString() : null)
                .createdAt(alert.getCreatedAt() != null ? alert.getCreatedAt().toString() : null)
                .build();
    }
}
