package com.xinya.business.clinical.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinya.business.clinical.dto.*;
import com.xinya.business.clinical.entity.ClinicalStageHistory;
import com.xinya.business.clinical.mapper.ClinicalStageHistoryMapper;
import com.xinya.business.clinical.service.ClinicalService;
import com.xinya.business.patient.entity.Patient;
import com.xinya.business.patient.mapper.PatientMapper;
import com.xinya.common.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClinicalServiceImpl implements ClinicalService {

    private final PatientMapper patientMapper;
    private final ClinicalStageHistoryMapper historyMapper;

    @Override
    public PatientStageInfoDto getPatientStageInfo(String patientId) {
        Patient patient = patientMapper.selectById(patientId);
        if (patient == null) throw new ResourceNotFoundException("患者不存在");
        return buildDto(patient);
    }

    @Override
    @Transactional
    public PatientStageInfoDto updatePatientStage(String patientId, UpdateStageRequest request) {
        Patient patient = patientMapper.selectById(patientId);
        if (patient == null) throw new ResourceNotFoundException("患者不存在");

        String oldStage = patient.getStage();
        patient.setStage(request.getNewStage());
        patient.setStageStartDate(LocalDate.now());
        patientMapper.updateById(patient);

        ClinicalStageHistory history = ClinicalStageHistory.builder()
                .patientId(patientId)
                .fromStage(oldStage)
                .toStage(request.getNewStage())
                .operatorNote(request.getReason())
                .transitionDate(LocalDate.now())
                .build();
        historyMapper.insert(history);
        return buildDto(patient);
    }

    @Override
    public List<ClinicalStageHistoryDto> getStageHistory(String patientId) {
        LambdaQueryWrapper<ClinicalStageHistory> wrapper = new LambdaQueryWrapper<ClinicalStageHistory>()
                .eq(ClinicalStageHistory::getPatientId, patientId)
                .orderByDesc(ClinicalStageHistory::getTransitionDate);
        return historyMapper.selectList(wrapper).stream()
                .map(h -> ClinicalStageHistoryDto.builder()
                        .id(h.getId())
                        .fromStage(h.getFromStage())
                        .toStage(h.getToStage())
                        .operatorId(h.getOperatorId())
                        .operatorNote(h.getOperatorNote())
                        .transitionDate(h.getTransitionDate() != null ? h.getTransitionDate().toString() : null)
                        .daysInStage(h.getDaysInStage())
                        .build())
                .collect(Collectors.toList());
    }

    private PatientStageInfoDto buildDto(Patient patient) {
        return PatientStageInfoDto.builder()
                .patientId(patient.getId())
                .patientName(patient.getName())
                .stage(patient.getStage())
                .stageStartDate(patient.getStageStartDate() != null ? patient.getStageStartDate().toString() : null)
                .admissionDate(patient.getAdmissionDate() != null ? patient.getAdmissionDate().toString() : null)
                .build();
    }
}
