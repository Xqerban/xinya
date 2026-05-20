package com.xinya.business.patient.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.business.alerts.mapper.AlertMapper;
import com.xinya.business.hopetree.entity.HopeTreeProgress;
import com.xinya.business.hopetree.mapper.HopeTreeGrowthHistoryMapper;
import com.xinya.business.hopetree.mapper.HopeTreeProgressMapper;
import com.xinya.business.patient.dto.*;
import com.xinya.business.patient.entity.Patient;
import com.xinya.business.patient.mapper.PatientMapper;
import com.xinya.business.patient.mapper.PsychEnergyLogMapper;
import com.xinya.business.patient.service.PatientService;
import com.xinya.business.pro.entity.ProRecord;
import com.xinya.business.pro.mapper.ProRecordMapper;
import com.xinya.common.core.domain.PageResult;
import com.xinya.common.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    private final PatientMapper patientMapper;
    private final HopeTreeProgressMapper hopeTreeProgressMapper;
    private final PsychEnergyLogMapper psychEnergyLogMapper;
    private final ProRecordMapper proRecordMapper;
    private final AlertMapper alertMapper;
    private final HopeTreeGrowthHistoryMapper hopeTreeGrowthHistoryMapper;

    @Override
    @Transactional
    public PatientDto createPatient(CreatePatientRequest request) {
        LocalDate admissionDate = LocalDate.parse(request.getAdmissionDate());
        String id = UUID.randomUUID().toString();

        Patient patient = Patient.builder()
                .id(id)
                .name(request.getName())
                .age(request.getAge())
                .gender(request.getGender())
                .diagnosis(request.getDiagnosis())
                .stage("ADMISSION")
                .stageStartDate(admissionDate)
                .psychEnergy(50)
                .treeLevel(1)
                .admissionDate(admissionDate)
                .roomNumber(request.getRoomNumber())
                .build();
        patientMapper.insert(patient);

        if (!hopeTreeProgressMapper.existsByPatientId(id)) {
            HopeTreeProgress progress = HopeTreeProgress.builder().patientId(id).build();
            hopeTreeProgressMapper.insert(progress);
        }
        return toDto(patient);
    }

    @Override
    public PatientDto getPatientById(String id) {
        Patient patient = patientMapper.selectById(id);
        if (patient == null) throw new ResourceNotFoundException("患者不存在");
        return toDto(patient);
    }

    @Override
    public PageResult<PatientDto> listPatients(Integer page, Integer pageSize, String stage, String keyword) {
        int pageIndex = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        Page<Patient> p = new Page<>(pageIndex, size);
        patientMapper.pageByStageAndKeyword(p,
                (stage != null && !stage.isBlank()) ? stage : null,
                (keyword != null && !keyword.isBlank()) ? keyword : null);
        return PageResult.<PatientDto>builder()
                .list(p.getRecords().stream().map(this::toDto).collect(Collectors.toList()))
                .total(p.getTotal())
                .page(pageIndex)
                .pageSize(size)
                .build();
    }

    @Override
    @Transactional
    public PatientDto updatePatient(String id, UpdatePatientRequest request) {
        Patient patient = patientMapper.selectById(id);
        if (patient == null) throw new ResourceNotFoundException("患者不存在");
        if (request.getName() != null) patient.setName(request.getName());
        if (request.getRoomNumber() != null) patient.setRoomNumber(request.getRoomNumber());
        if (request.getDiagnosis() != null) patient.setDiagnosis(request.getDiagnosis());
        if (request.getAge() != null) patient.setAge(request.getAge());
        if (request.getGender() != null) patient.setGender(request.getGender());
        patientMapper.updateById(patient);
        return toDto(patient);
    }

    @Override
    @Transactional
    public void deletePatient(String id) {
        if (patientMapper.selectById(id) == null) throw new ResourceNotFoundException("患者不存在");
        patientMapper.deleteById(id);
    }

    @Override
    public EnergyTrendResponse getEnergyTrend(String patientId, Integer days) {
        if (patientMapper.selectById(patientId) == null) throw new ResourceNotFoundException("患者不存在");
        int d = (days == null || days <= 0) ? 7 : days;
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(d - 1L);
        List<Object[]> rows = psychEnergyLogMapper.findDailyEnergyTrend(patientId, start, end);
        List<EnergyTrendPoint> trend = rows.stream()
                .map(row -> {
                    Object dateObj = row[0];
                    Object energyObj = row[1];
                    LocalDate date;
                    if (dateObj instanceof LocalDate) {
                        date = (LocalDate) dateObj;
                    } else if (dateObj instanceof java.sql.Date) {
                        date = ((java.sql.Date) dateObj).toLocalDate();
                    } else {
                        date = LocalDate.parse(Objects.toString(dateObj));
                    }
                    Integer energy = energyObj != null ? ((Number) energyObj).intValue() : null;
                    return EnergyTrendPoint.builder().date(date.toString()).psychEnergy(energy).build();
                })
                .collect(Collectors.toList());
        Double avg = psychEnergyLogMapper.avgEnergyBetween(patientId, start, end);
        Integer min = psychEnergyLogMapper.minEnergyBetween(patientId, start, end);
        Integer max = psychEnergyLogMapper.maxEnergyBetween(patientId, start, end);
        return EnergyTrendResponse.builder()
                .patientId(patientId)
                .trend(trend)
                .avgEnergy(avg != null ? avg : 0.0)
                .minEnergy(min)
                .maxEnergy(max)
                .build();
    }

    @Override
    public PatientDetailDto getPatientDetail(String patientId) {
        PatientDto patient = getPatientById(patientId);
        HopeTreeDto hopeTree = buildHopeTreeDto(patientId);
        LatestProRecordDto latestProRecord = buildLatestProRecordDto(patientId);
        boolean todayCheckedIn = proRecordMapper.existsByPatientIdAndRecordDate(patientId, LocalDate.now());
        int pendingAlerts = (int) alertMapper.countByPatientIdAndResolvedFalse(patientId);
        return PatientDetailDto.builder()
                .patient(patient)
                .hopeTree(hopeTree)
                .latestProRecord(latestProRecord)
                .todayCheckedIn(todayCheckedIn)
                .pendingAlerts(pendingAlerts)
                .build();
    }

    private HopeTreeDto buildHopeTreeDto(String patientId) {
        HopeTreeProgress progress = hopeTreeProgressMapper.findByPatientId(patientId);
        if (progress == null) return null;
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        int todayExp = hopeTreeGrowthHistoryMapper.sumTodayExp(patientId, dayStart, dayEnd);
        return HopeTreeDto.builder()
                .currentLevel(progress.getCurrentLevel())
                .currentExp(progress.getCurrentExp())
                .nextLevelExp(progress.getNextLevelExp())
                .totalGrowthDays(progress.getTotalGrowthDays())
                .levelName(resolveLevelName(progress.getCurrentLevel()))
                .levelImageUrl("/assets/hopetree/level" + progress.getCurrentLevel() + ".png")
                .todayExpGained(todayExp)
                .lastGrowthTime(progress.getLastGrowthDate() != null ? progress.getLastGrowthDate().toLocalDate().toString() : null)
                .build();
    }

    public long countUnresolved(String patientId) {
        return alertMapper.countByPatientIdAndResolvedFalse(patientId);
    }

    private LatestProRecordDto buildLatestProRecordDto(String patientId) {
        Page<ProRecord> page = new Page<>(1, 1);
        proRecordMapper.findByPatientIdAndDateRangeOrderByDesc(page, patientId,
                LocalDate.now().minusDays(365), LocalDate.now());
        if (page.getRecords().isEmpty()) return null;
        ProRecord latest = page.getRecords().get(0);
        LocalDate date = latest.getRecordDate();
        int totalScore = proRecordMapper.sumScoreByPatientIdAndDate(patientId, date);
        int answerCount = proRecordMapper.findByPatientIdAndRecordDateOrderByCreatedAtAsc(patientId, date).size();
        return LatestProRecordDto.builder()
                .recordDate(date.toString())
                .totalScore(totalScore)
                .answerCount(answerCount)
                .build();
    }

    private PatientDto toDto(Patient patient) {
        return PatientDto.builder()
                .id(patient.getId())
                .name(patient.getName())
                .stage(patient.getStage())
                .psychEnergy(patient.getPsychEnergy())
                .treeLevel(patient.getTreeLevel())
                .admissionDate(patient.getAdmissionDate() != null ? patient.getAdmissionDate().toString() : null)
                .roomNumber(patient.getRoomNumber())
                .createdAt(patient.getCreatedAt() != null ? patient.getCreatedAt().toString() : null)
                .updatedAt(patient.getUpdatedAt() != null ? patient.getUpdatedAt().toString() : null)
                .build();
    }

    private String resolveLevelName(Integer level) {
        if (level == null) return null;
        return switch (level) {
            case 1 -> "新芽初生";
            case 2 -> "茁壮成长";
            case 3 -> "枝繁叶茂";
            case 4 -> "绿荫如盖";
            case 5 -> "参天大树";
            case 6 -> "守护之树";
            case 7 -> "希望之树";
            default -> "Lv." + level;
        };
    }
}
