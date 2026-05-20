package com.xinya.business.pro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.business.patient.entity.Patient;
import com.xinya.business.patient.mapper.PatientMapper;
import com.xinya.business.patient.mapper.PsychEnergyLogMapper;
import com.xinya.business.patient.entity.PsychEnergyLog;
import com.xinya.business.pro.dto.*;
import com.xinya.business.pro.entity.ProQuestion;
import com.xinya.business.pro.entity.ProRecord;
import com.xinya.business.pro.mapper.ProQuestionMapper;
import com.xinya.business.pro.mapper.ProRecordMapper;
import com.xinya.business.pro.service.ProService;
import com.xinya.common.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProServiceImpl implements ProService {

    private final ProQuestionMapper proQuestionMapper;
    private final ProRecordMapper proRecordMapper;
    private final PatientMapper patientMapper;
    private final PsychEnergyLogMapper psychEnergyLogMapper;

    @Override
    public List<ProQuestionDto> getQuestions(String stage) {
        LambdaQueryWrapper<ProQuestion> wrapper = new LambdaQueryWrapper<ProQuestion>()
                .eq(ProQuestion::getIsActive, true)
                .orderByAsc(ProQuestion::getSortOrder);
        if (stage != null && !stage.isBlank()) {
            wrapper.eq(ProQuestion::getStage, stage);
        }
        return proQuestionMapper.selectList(wrapper).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProRecordResultDto submitProRecord(SubmitProRecordRequest request) {
        Patient patient = patientMapper.selectById(request.getPatientId());
        if (patient == null) throw new ResourceNotFoundException("患者不存在");

        LocalDate date = LocalDate.now();
        if (proRecordMapper.existsByPatientIdAndRecordDate(request.getPatientId(), date)) {
            throw new IllegalStateException("今日已提交");
        }

        int totalScore = 0;
        List<ProRecord> records = new ArrayList<>();
        for (ProAnswerDto ans : request.getAnswers()) {
            ProQuestion q = proQuestionMapper.selectById(ans.getQuestionId());
            if (q == null) continue;
            int score = parseScore(ans.getAnswer(), q.getType());
            totalScore += score;
            records.add(ProRecord.builder()
                    .patientId(request.getPatientId())
                    .questionId(ans.getQuestionId())
                    .answer(ans.getAnswer())
                    .answerScore(score)
                    .recordDate(date)
                    .build());
        }
        for (ProRecord r : records) proRecordMapper.insert(r);

        int energyDelta = calcEnergyDelta(totalScore);
        int newEnergy = Math.min(100, Math.max(0,
                (patient.getPsychEnergy() != null ? patient.getPsychEnergy() : 50) + energyDelta));
        patient.setPsychEnergy(newEnergy);
        patientMapper.updateById(patient);

        PsychEnergyLog log = PsychEnergyLog.builder()
                .patientId(request.getPatientId())
                .psychEnergy(newEnergy)
                .delta(energyDelta)
                .triggerType("PRO_SUBMISSION")
                .build();
        psychEnergyLogMapper.insert(log);

        return ProRecordResultDto.builder()
                .patientId(request.getPatientId())
                .recordDate(date.toString())
                .totalScore(totalScore)
                .psychEnergyDelta(energyDelta)
                .newPsychEnergy(newEnergy)
                .answers(request.getAnswers())
                .build();
    }

    @Override
    public ProRecordResultDto getProResult(String patientId, String recordDate) {
        LocalDate date = recordDate != null ? LocalDate.parse(recordDate) : LocalDate.now();
        List<ProRecord> records = proRecordMapper.findByPatientIdAndRecordDateOrderByCreatedAtAsc(patientId, date);
        int totalScore = records.stream()
                .mapToInt(r -> r.getAnswerScore() != null ? r.getAnswerScore() : 0).sum();
        List<ProAnswerDto> answers = records.stream()
                .map(r -> new ProAnswerDto(r.getQuestionId(), r.getAnswer()))
                .collect(Collectors.toList());
        return ProRecordResultDto.builder()
                .patientId(patientId)
                .recordDate(date.toString())
                .totalScore(totalScore)
                .answers(answers)
                .build();
    }

    @Override
    public List<ProQuestionDto> getActiveQuestions() {
        return getQuestions(null);
    }

    private int parseScore(String answer, String type) {
        if (answer == null || answer.isBlank()) return 0;
        try {
            if ("scale".equalsIgnoreCase(type) || "single_choice".equalsIgnoreCase(type)) {
                return Integer.parseInt(answer.trim());
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }

    private int calcEnergyDelta(int totalScore) {
        if (totalScore >= 80) return 10;
        if (totalScore >= 60) return 5;
        if (totalScore >= 40) return 0;
        if (totalScore >= 20) return -5;
        return -10;
    }

    private ProQuestionDto toDto(ProQuestion q) {
        return ProQuestionDto.builder()
                .id(q.getId())
                .stage(q.getStage())
                .title(q.getTitle())
                .type(q.getType())
                .options(q.getOptions())
                .scaleMin(q.getScaleMin())
                .scaleMax(q.getScaleMax())
                .minLabel(q.getMinLabel())
                .maxLabel(q.getMaxLabel())
                .symptomKey(q.getSymptomKey())
                .sortOrder(q.getSortOrder())
                .build();
    }
}
