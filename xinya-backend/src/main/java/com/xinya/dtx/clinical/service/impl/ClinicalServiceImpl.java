package com.xinya.dtx.clinical.service.impl;

import com.xinya.dtx.clinical.dto.ClinicalStageHistoryDto;
import com.xinya.dtx.clinical.dto.ClinicalStageInfoDto;
import com.xinya.dtx.clinical.dto.StageTransitionRequest;
import com.xinya.dtx.clinical.service.ClinicalService;
import com.xinya.dtx.patient.dto.PatientDto;
import com.xinya.dtx.patient.entity.Patient;
import com.xinya.dtx.patient.mapper.PatientMapper;
import com.xinya.dtx.patient.service.PatientService;
import com.xinya.dtx.clinical.entity.ClinicalStageHistory;
import com.xinya.dtx.hopetree.entity.HopeTreeProgress;
import com.xinya.dtx.hopetree.entity.HopeTreeGrowthHistory;
import com.xinya.dtx.clinical.mapper.ClinicalStageHistoryMapper;
import com.xinya.dtx.hopetree.mapper.HopeTreeGrowthHistoryMapper;
import com.xinya.dtx.hopetree.mapper.HopeTreeProgressMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClinicalServiceImpl implements ClinicalService {

    private static final int STAGE_ADVANCE_EXP = 50;

    private final PatientMapper patientMapper;
    private final ClinicalStageHistoryMapper clinicalStageHistoryMapper;
    private final HopeTreeProgressMapper hopeTreeProgressMapper;
    private final HopeTreeGrowthHistoryMapper hopeTreeGrowthHistoryMapper;
    private final PatientService patientService;

    private static final Map<String, Integer> STAGE_ORDER = Map.of(
            "ADMISSION", 1,
            "PRETREATMENT", 2,
            "TRANSPLANT", 3,
            "REBUILD", 4,
            "DISCHARGE", 5
    );

    @Override
    @Transactional
    public ClinicalStageInfoDto getCurrentStage(String patientId) {
        Patient patient = patientMapper.findById(patientId)
                .orElseThrow(() -> new EntityNotFoundException("患者不存在"));

        String stage = patient.getStage();
        String stageName = resolveStageName(stage);
        Integer order = STAGE_ORDER.getOrDefault(stage, 0);
        LocalDate startDate = patient.getStageStartDate();
        int daysInStage = 0;
        if (startDate != null) {
            long days = ChronoUnit.DAYS.between(startDate, LocalDate.now());
            daysInStage = (int) Math.max(days + 1, 1);
        }

        return ClinicalStageInfoDto.builder()
                .stage(stage)
                .stageName(stageName)
                .stageOrder(order)
                .stageStartDate(startDate != null ? startDate.toString() : null)
                .daysInStage(daysInStage)
                .build();
    }

    @Override
    @Transactional
    public PatientDto transition(StageTransitionRequest request) {
        Patient patient = patientMapper.findById(request.getPatientId())
                .orElseThrow(() -> new EntityNotFoundException("患者不存在"));

        String currentStage = patient.getStage();
        String targetStage = request.getTargetStage();

        if (currentStage.equals(targetStage)) {
            throw new IllegalStateException("目标阶段与当前阶段相同");
        }

        int currentOrder = STAGE_ORDER.getOrDefault(currentStage, 0);
        int targetOrder = STAGE_ORDER.getOrDefault(targetStage, 0);

        if (currentOrder == 0 || targetOrder == 0 || Math.abs(targetOrder - currentOrder) != 1) {
            throw new IllegalStateException("非法阶段流转");
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate = patient.getStageStartDate();
        int daysInStage = 0;
        if (startDate != null) {
            long days = ChronoUnit.DAYS.between(startDate, today);
            daysInStage = (int) Math.max(days, 0);
        }

        ClinicalStageHistory history = ClinicalStageHistory.builder()
                .patientId(patient.getId())
                .fromStage(currentStage)
                .toStage(targetStage)
                .transitionDate(today)
                .daysInStage(daysInStage)
                .operatorId(null)
                .operatorName(null)
                .operatorNote(request.getOperatorNote())
                .build();
        history = clinicalStageHistoryMapper.save(history);

        // 更新患者当前阶段及阶段开始日期
        patientMapper.updateStage(patient.getId(), targetStage);

        // 希望之树阶段奖励 +50 exp
        HopeTreeProgress progress = hopeTreeProgressMapper.findByPatientId(patient.getId())
                .orElse(null);
        if (progress != null) {
            int levelBefore = progress.getCurrentLevel();
            hopeTreeProgressMapper.addExp(patient.getId(), STAGE_ADVANCE_EXP, LocalDateTime.now());

            HopeTreeGrowthHistory growth = HopeTreeGrowthHistory.builder()
                    .patientId(patient.getId())
                    .growthSource("stage_advance")
                    .expAmount(STAGE_ADVANCE_EXP)
                    .levelBefore(levelBefore)
                    .levelAfter(levelBefore)
                    .levelUp(false)
                    .sourceRefId("clinical-" + history.getId())
                    .build();
            hopeTreeGrowthHistoryMapper.save(growth);
        }

        // 返回更新后的 PatientDto
        return patientService.getPatientById(patient.getId());
    }

    @Override
    @Transactional
    public List<ClinicalStageHistoryDto> listHistory(String patientId) {
        if (!patientMapper.existsById(patientId)) {
            throw new EntityNotFoundException("患者不存在");
        }
        List<ClinicalStageHistory> list = clinicalStageHistoryMapper.findByPatientIdOrderByCreatedAtDesc(patientId);
        return list.stream()
                .map(h -> ClinicalStageHistoryDto.builder()
                        .id(h.getId())
                        .fromStage(h.getFromStage())
                        .toStage(h.getToStage())
                        .transitionDate(h.getTransitionDate() != null ? h.getTransitionDate().toString() : null)
                        .operatorId(h.getOperatorId())
                        .operatorNote(h.getOperatorNote())
                        .daysInStage(h.getDaysInStage())
                        .build())
                .collect(Collectors.toList());
    }

    private String resolveStageName(String stage) {
        if (stage == null) {
            return null;
        }
        return switch (stage) {
            case "ADMISSION" -> "入仓期";
            case "PRETREATMENT" -> "预处理期";
            case "TRANSPLANT" -> "移植期";
            case "REBUILD" -> "重建期";
            case "DISCHARGE" -> "出仓期";
            default -> stage;
        };
    }
}

