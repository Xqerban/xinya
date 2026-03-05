package com.xinya.dtx.alerts.service.impl;

import com.xinya.dtx.alerts.dto.AlertDto;
import com.xinya.dtx.alerts.dto.AlertListResponse;
import com.xinya.dtx.alerts.dto.CreateAlertRequest;
import com.xinya.dtx.alerts.dto.ResolveAlertRequest;
import com.xinya.dtx.alerts.service.AlertService;
import com.xinya.dtx.patient.entity.Patient;
import com.xinya.dtx.patient.mapper.PatientMapper;
import com.xinya.dtx.alerts.entity.Alert;
import com.xinya.dtx.alerts.mapper.AlertMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final AlertMapper alertMapper;
    private final PatientMapper patientMapper;

    @Override
    @Transactional
    public AlertListResponse list(Boolean resolved, String level, String patientId,
                                  Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 0 : page - 1;
        int size = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        Pageable pageable = PageRequest.of(p, size);
        Page<Alert> alertPage = alertMapper.findByFilters(resolved, level, patientId, pageable);
        List<AlertDto> list = alertPage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        long unresolvedCount = alertMapper.countByResolvedFalse();
        return AlertListResponse.builder()
                .list(list)
                .total(alertPage.getTotalElements())
                .unresolvedCount(unresolvedCount)
                .build();
    }

    @Override
    @Transactional
    public AlertDto resolve(String id, ResolveAlertRequest request) {
        String note = request != null ? request.getResolvedNote() : null;
        LocalDateTime now = LocalDateTime.now();
        int updated = alertMapper.resolve(id, null, note, now);
        if (updated == 0) {
            throw new EntityNotFoundException("预警不存在或已处理");
        }
        Alert alert = alertMapper.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("预警不存在"));
        return toDto(alert);
    }

    @Override
    @Transactional
    public AlertDto create(CreateAlertRequest request) {
        Patient patient = patientMapper.findById(request.getPatientId())
                .orElseThrow(() -> new EntityNotFoundException("患者不存在"));

        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .patientId(patient.getId())
                .patientName(patient.getName())
                .alertType(request.getAlertType())
                .level(request.getLevel())
                .message(request.getMessage())
                .resolved(false)
                .build();
        alertMapper.save(alert);
        return toDto(alert);
    }

    private AlertDto toDto(Alert a) {
        return AlertDto.builder()
                .id(a.getId())
                .patientId(a.getPatientId())
                .patientName(a.getPatientName())
                .alertType(a.getAlertType())
                .level(a.getLevel())
                .message(a.getMessage())
                .triggerMessage(a.getTriggerMessage())
                .resolved(Boolean.TRUE.equals(a.getResolved()))
                .resolvedBy(a.getResolvedBy())
                .resolvedNote(a.getResolvedNote())
                .resolvedAt(a.getResolvedAt() != null ? a.getResolvedAt().toString() : null)
                .createdAt(a.getCreatedAt() != null ? a.getCreatedAt().toString() : null)
                .build();
    }
}

