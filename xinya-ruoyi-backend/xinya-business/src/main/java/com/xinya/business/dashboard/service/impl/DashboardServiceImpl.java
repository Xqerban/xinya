package com.xinya.business.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinya.business.alerts.mapper.AlertMapper;
import com.xinya.business.dashboard.dto.AlertItemDto;
import com.xinya.business.dashboard.dto.DashboardDto;
import com.xinya.business.dashboard.dto.PsychDistributionDto;
import com.xinya.business.dashboard.service.DashboardService;
import com.xinya.business.patient.entity.Patient;
import com.xinya.business.patient.mapper.PatientMapper;
import com.xinya.business.pro.mapper.ProRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final PatientMapper patientMapper;
    private final AlertMapper alertMapper;
    private final ProRecordMapper proRecordMapper;

    @Override
    public DashboardDto getDashboardStats() {
        long totalPatients = patientMapper.countTotal();
        double avgPsychEnergy = patientMapper.avgPsychEnergy() != null ? patientMapper.avgPsychEnergy() : 0.0;
        long lowEnergyCount = patientMapper.countByPsychEnergyLessThan(30);
        long todayCheckInCount = proRecordMapper.countByRecordDate(LocalDate.now());
        double checkInRate = totalPatients > 0 ? (double) todayCheckInCount / totalPatients * 100 : 0.0;

        // 按阶段统计
        List<Map<String, Object>> stageList = patientMapper.countByStage();
        Map<String, Long> patientsByStage = new HashMap<>();
        for (Map<String, Object> row : stageList) {
            String stage = (String) row.get("stage");
            Object countObj = row.get("count");
            long count = countObj != null ? ((Number) countObj).longValue() : 0L;
            patientsByStage.put(stage, count);
        }

        // 最新未处理告警
        List<AlertItemDto> alerts = alertMapper.findLatestUnresolved(5).stream()
                .map(a -> AlertItemDto.builder()
                        .id(a.getId())
                        .patientId(a.getPatientId())
                        .patientName(a.getPatientName())
                        .level(a.getLevel())
                        .message(a.getMessage())
                        .createdAt(a.getCreatedAt() != null ? a.getCreatedAt().toString() : null)
                        .build())
                .collect(Collectors.toList());

        return DashboardDto.builder()
                .totalPatients(totalPatients)
                .patientsByStage(patientsByStage)
                .avgPsychEnergy(avgPsychEnergy)
                .lowEnergyCount(lowEnergyCount)
                .todayCheckInCount(todayCheckInCount)
                .checkInRate(checkInRate)
                .alerts(alerts)
                .build();
    }

    @Override
    public PsychDistributionDto getPsychDistribution() {
        long total = patientMapper.countTotal();
        long warningCount = patientMapper.countByPsychEnergyLessThan(40);
        long mildCount   = patientMapper.countByPsychEnergyLessThan(70) - warningCount;
        long healthyCount = total - mildCount - warningCount;

        double toPercent = total > 0 ? 100.0 / total : 0.0;
        return PsychDistributionDto.builder()
                .healthy(PsychDistributionDto.Band.builder()
                        .count(healthyCount).range("70-100")
                        .percent(Math.round(healthyCount * toPercent * 10.0) / 10.0).build())
                .mild(PsychDistributionDto.Band.builder()
                        .count(mildCount).range("40-69")
                        .percent(Math.round(mildCount * toPercent * 10.0) / 10.0).build())
                .warning(PsychDistributionDto.Band.builder()
                        .count(warningCount).range("0-39")
                        .percent(Math.round(warningCount * toPercent * 10.0) / 10.0).build())
                .build();
    }

    @Override
    public Map<String, Object> getPatientReport(String patientId) {
        Patient patient = patientMapper.selectById(patientId);
        Map<String, Object> report = new HashMap<>();
        if (patient == null) return report;

        report.put("patientId", patient.getId());
        report.put("name", patient.getName());
        report.put("stage", patient.getStage());
        report.put("psychEnergy", patient.getPsychEnergy());
        report.put("treeLevel", patient.getTreeLevel());
        report.put("admissionDate", patient.getAdmissionDate());

        long checkInDays = proRecordMapper.selectCount(
                new LambdaQueryWrapper<com.xinya.business.pro.entity.ProRecord>()
                        .eq(com.xinya.business.pro.entity.ProRecord::getPatientId, patientId));
        report.put("checkInDays", checkInDays);

        long unresolvedAlerts = alertMapper.selectCount(
                new LambdaQueryWrapper<com.xinya.business.alerts.entity.Alert>()
                        .eq(com.xinya.business.alerts.entity.Alert::getPatientId, patientId)
                        .eq(com.xinya.business.alerts.entity.Alert::getResolved, false));
        report.put("unresolvedAlerts", unresolvedAlerts);

        return report;
    }
}
