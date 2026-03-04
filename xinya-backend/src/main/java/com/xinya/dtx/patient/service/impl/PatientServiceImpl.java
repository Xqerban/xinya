package com.xinya.dtx.patient.service.impl;

import com.xinya.dtx.common.response.PageResult;
import com.xinya.dtx.patient.dto.CreatePatientRequest;
import com.xinya.dtx.patient.dto.EnergyTrendPoint;
import com.xinya.dtx.patient.dto.EnergyTrendResponse;
import com.xinya.dtx.patient.dto.HopeTreeDto;
import com.xinya.dtx.patient.dto.LatestProRecordDto;
import com.xinya.dtx.patient.dto.PatientDetailDto;
import com.xinya.dtx.patient.dto.PatientDto;
import com.xinya.dtx.patient.dto.UpdatePatientRequest;
import com.xinya.dtx.patient.service.PatientService;
import com.xinya.dtx.system.entity.HopeTreeProgress;
import com.xinya.dtx.pro.entity.ProRecord;
import com.xinya.dtx.patient.entity.Patient;
import com.xinya.dtx.system.mapper.AlertMapper;
import com.xinya.dtx.system.mapper.HopeTreeGrowthHistoryMapper;
import com.xinya.dtx.system.mapper.HopeTreeProgressMapper;
import com.xinya.dtx.pro.mapper.ProRecordMapper;
import com.xinya.dtx.system.mapper.PsychEnergyLogMapper;
import com.xinya.dtx.patient.mapper.PatientMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Objects;
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

        patient = patientMapper.save(patient);

        // 初始化希望之树进度（如果不存在）
        if (!hopeTreeProgressMapper.existsByPatientId(id)) {
            HopeTreeProgress progress = HopeTreeProgress.builder()
                    .patientId(id)
                    .build();
            hopeTreeProgressMapper.save(progress);
        }

        return toDto(patient);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientDto getPatientById(String id) {
        Patient patient = patientMapper.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("患者不存在"));
        return toDto(patient);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PatientDto> listPatients(Integer page, Integer pageSize, String stage, String keyword) {
        int pageIndex = page == null || page < 1 ? 0 : page - 1;
        int size = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        Pageable pageable = PageRequest.of(pageIndex, size);

        Page<Patient> patientPage;
        boolean hasStage = stage != null && !stage.isBlank();
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (hasStage && hasKeyword) {
            patientPage = patientMapper.findByStageAndNameContaining(stage, keyword, pageable);
        } else if (hasStage) {
            patientPage = patientMapper.findByStage(stage, pageable);
        } else if (hasKeyword) {
            patientPage = patientMapper.findByNameContaining(keyword, pageable);
        } else {
            patientPage = patientMapper.findAll(pageable);
        }

        return PageResult.<PatientDto>builder()
                .list(patientPage.getContent().stream().map(this::toDto).collect(Collectors.toList()))
                .total(patientPage.getTotalElements())
                .page(pageIndex + 1)
                .pageSize(size)
                .build();
    }

    @Override
    @Transactional
    public PatientDto updatePatient(String id, UpdatePatientRequest request) {
        Patient patient = patientMapper.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("患者不存在"));

        if (request.getName() != null) {
            patient.setName(request.getName());
        }
        if (request.getRoomNumber() != null) {
            patient.setRoomNumber(request.getRoomNumber());
        }
        if (request.getDiagnosis() != null) {
            patient.setDiagnosis(request.getDiagnosis());
        }
        if (request.getAge() != null) {
            patient.setAge(request.getAge());
        }
        if (request.getGender() != null) {
            patient.setGender(request.getGender());
        }

        patient = patientMapper.save(patient);
        return toDto(patient);
    }

    @Override
    @Transactional
    public void deletePatient(String id) {
        if (!patientMapper.existsById(id)) {
            throw new EntityNotFoundException("患者不存在");
        }
        patientMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public EnergyTrendResponse getEnergyTrend(String patientId, Integer days) {
        if (!patientMapper.existsById(patientId)) {
            throw new EntityNotFoundException("患者不存在");
        }

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
                    return EnergyTrendPoint.builder()
                            .date(date.toString())
                            .psychEnergy(energy)
                            .build();
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
    @Transactional(readOnly = true)
    public PatientDetailDto getPatientDetail(String patientId) {
        PatientDto patient = getPatientById(patientId);

        HopeTreeDto hopeTree = buildHopeTreeDto(patientId);

        LatestProRecordDto latestProRecord = buildLatestProRecordDto(patientId);

        boolean todayCheckedIn = proRecordMapper.existsByPatientIdAndRecordDate(
                patientId, LocalDate.now());

        int pendingAlerts = (int) alertMapper.countByPatientIdAndResolvedFalse(patientId);

        return PatientDetailDto.builder()
            .patient(patient)
            .hopeTree(hopeTree)
            .latestProRecord(latestProRecord)
            .todayCheckedIn(todayCheckedIn)
            .pendingAlerts(pendingAlerts)
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

    private HopeTreeDto buildHopeTreeDto(String patientId) {
        return hopeTreeProgressMapper.findByPatientId(patientId)
                .map(progress -> {
                    LocalDateTime lastGrowth = progress.getLastGrowthDate();
                    // 今日经验值
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
                            .levelImageUrl(resolveLevelImageUrl(progress.getCurrentLevel()))
                            .todayExpGained(todayExp)
                            .lastGrowthTime(lastGrowth != null ? lastGrowth.toString() : null)
                            .build();
                })
                .orElse(null);
    }

    private LatestProRecordDto buildLatestProRecordDto(String patientId) {
        Page<ProRecord> page = proRecordMapper.findByPatientIdOrderByRecordDateDesc(
                patientId, PageRequest.of(0, 1));
        if (page.isEmpty()) {
            return null;
        }
        ProRecord latest = page.getContent().get(0);
        LocalDate date = latest.getRecordDate();
        int totalScore = proRecordMapper.sumScoreByPatientIdAndDate(patientId, date);
        int answerCount = proRecordMapper.findByPatientIdAndRecordDateOrderByCreatedAtAsc(
                patientId, date).size();
        return LatestProRecordDto.builder()
                .recordDate(date.toString())
                .totalScore(totalScore)
                .answerCount(answerCount)
                .build();
    }

    private String resolveLevelName(Integer level) {
        if (level == null) {
            return null;
        }
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

    private String resolveLevelImageUrl(Integer level) {
        if (level == null) {
            return null;
        }
        return "/assets/hopetree/level" + level + ".png";
    }
}

