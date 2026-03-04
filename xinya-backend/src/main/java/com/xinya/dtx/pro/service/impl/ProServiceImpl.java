package com.xinya.dtx.pro.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinya.dtx.patient.entity.Patient;
import com.xinya.dtx.patient.mapper.PatientMapper;
import com.xinya.dtx.pro.dto.ProAnswerDto;
import com.xinya.dtx.pro.dto.ProHistoryAnswerDto;
import com.xinya.dtx.pro.dto.ProHistoryItemDto;
import com.xinya.dtx.pro.dto.ProHistoryPageDto;
import com.xinya.dtx.pro.dto.ProQuestionDto;
import com.xinya.dtx.pro.dto.ProQuestionListDto;
import com.xinya.dtx.pro.dto.ProSubmitRequest;
import com.xinya.dtx.pro.dto.ProSubmitResultDto;
import com.xinya.dtx.pro.dto.SymptomTrendPointDto;
import com.xinya.dtx.pro.dto.SymptomTrendResponseDto;
import com.xinya.dtx.pro.service.ProService;
import com.xinya.dtx.system.entity.Alert;
import com.xinya.dtx.system.entity.HopeTreeGrowthHistory;
import com.xinya.dtx.system.entity.HopeTreeProgress;
import com.xinya.dtx.pro.entity.ProQuestion;
import com.xinya.dtx.pro.entity.ProRecord;
import com.xinya.dtx.system.entity.PsychEnergyLog;
import com.xinya.dtx.system.mapper.AlertMapper;
import com.xinya.dtx.system.mapper.HopeTreeGrowthHistoryMapper;
import com.xinya.dtx.system.mapper.HopeTreeProgressMapper;
import com.xinya.dtx.pro.mapper.ProQuestionMapper;
import com.xinya.dtx.pro.mapper.ProRecordMapper;
import com.xinya.dtx.system.mapper.PsychEnergyLogMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProServiceImpl implements ProService {

    private static final int CHECKIN_PSYCH_ENERGY_DELTA = 10;
    private static final int CHECKIN_HOPE_TREE_EXP_DELTA = 20;
    private static final int SYMPTOM_ALERT_THRESHOLD = 10;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final PatientMapper patientMapper;
    private final ProQuestionMapper proQuestionMapper;
    private final ProRecordMapper proRecordMapper;
    private final PsychEnergyLogMapper psychEnergyLogMapper;
    private final HopeTreeProgressMapper hopeTreeProgressMapper;
    private final HopeTreeGrowthHistoryMapper hopeTreeGrowthHistoryMapper;
    private final AlertMapper alertMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(readOnly = true)
    public ProQuestionListDto getTodayQuestions(String patientId) {
        Patient patient = patientMapper.findById(patientId)
                .orElseThrow(() -> new EntityNotFoundException("患者不存在"));

        LocalDate today = LocalDate.now();
        boolean checkedInToday = proRecordMapper.existsByPatientIdAndRecordDate(patientId, today);

        String stage = patient.getStage();
        List<ProQuestion> questions = proQuestionMapper.findActiveByStage(stage);

        List<ProQuestionDto> dtoList = questions.stream()
                .map(q -> {
                    JsonNode options = null;
                    if (StringUtils.hasText(q.getOptions())) {
                        try {
                            options = objectMapper.readTree(q.getOptions());
                        } catch (Exception ignored) {
                            options = null;
                        }
                    }
                    return ProQuestionDto.builder()
                            .id(q.getId())
                            .title(q.getTitle())
                            .type(q.getType())
                            .options(options)
                            .min(q.getScaleMin())
                            .max(q.getScaleMax())
                            .minLabel(q.getMinLabel())
                            .maxLabel(q.getMaxLabel())
                            .build();
                })
                .collect(Collectors.toList());

        return ProQuestionListDto.builder()
                .checkedInToday(checkedInToday)
                .questions(dtoList)
                .build();
    }

    @Override
    @Transactional
    public ProSubmitResultDto submit(ProSubmitRequest request) {
        Patient patient = patientMapper.findById(request.getPatientId())
                .orElseThrow(() -> new EntityNotFoundException("患者不存在"));

        LocalDate date;
        if (StringUtils.hasText(request.getRecordDate())) {
            date = LocalDate.parse(request.getRecordDate(), DATE_FORMATTER);
        } else {
            date = LocalDate.now();
        }

        if (proRecordMapper.existsByPatientIdAndRecordDate(patient.getId(), date)) {
            throw new IllegalStateException("今日已打卡");
        }

        List<ProAnswerDto> answers = request.getAnswers();
        if (answers == null) {
            answers = new ArrayList<>();
        }

        // 预加载题目
        Map<String, ProQuestion> questionMap = new HashMap<>();
        if (!answers.isEmpty()) {
            List<String> ids = answers.stream()
                    .map(ProAnswerDto::getQuestionId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
            if (!ids.isEmpty()) {
                proQuestionMapper.findAllById(ids)
                        .forEach(q -> questionMap.put(q.getId(), q));
            }
        }

        int totalScore = 0;
        Long clientTs = request.getClientTimestamp();

        for (ProAnswerDto a : answers) {
            if (!StringUtils.hasText(a.getQuestionId())) {
                continue;
            }
            ProQuestion q = questionMap.get(a.getQuestionId());
            String title = q != null ? q.getTitle() : null;
            String symptomKey = q != null ? q.getSymptomKey() : null;
            int score = a.getScore() != null ? a.getScore() : 0;

            ProRecord record = ProRecord.builder()
                    .patientId(patient.getId())
                    .recordDate(date)
                    .questionId(a.getQuestionId())
                    .questionTitle(title)
                    .answer(a.getAnswer())
                    .answerScore(score)
                    .symptomKey(symptomKey)
                    .clientTimestamp(clientTs)
                    .build();
            proRecordMapper.save(record);
            totalScore += score;
        }

        // 心理能量 +10
        updatePsychEnergy(patient, CHECKIN_PSYCH_ENERGY_DELTA, "pro_checkin",
                "pro-" + patient.getId() + "-" + date);

        // 希望之树经验 +20
        addHopeTreeExp(patient, CHECKIN_HOPE_TREE_EXP_DELTA, "check_in",
                "pro-" + patient.getId() + "-" + date);

        // 症状告警（简单按总分阈值）
        boolean alertCreated = false;
        if (totalScore >= SYMPTOM_ALERT_THRESHOLD) {
            Alert alert = Alert.builder()
                    .id(UUID.randomUUID().toString())
                    .patientId(patient.getId())
                    .patientName(patient.getName())
                    .alertType("symptom")
                    .level("info")
                    .message("PRO 打卡总分为 " + totalScore + "，请关注患者症状变化。")
                    .resolved(false)
                    .build();
            alertMapper.save(alert);
            alertCreated = true;
        }

        return ProSubmitResultDto.builder()
                .success(true)
                .psychEnergyDelta(CHECKIN_PSYCH_ENERGY_DELTA)
                .hopeTreeExpDelta(CHECKIN_HOPE_TREE_EXP_DELTA)
                .totalScore(totalScore)
                .alertCreated(alertCreated)
                .message("打卡成功！您的希望之树获得了成长能量。")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProHistoryPageDto getHistory(String patientId, String startDateStr, String endDateStr,
                                        Integer page, Integer pageSize) {
        if (!patientMapper.existsById(patientId)) {
            throw new EntityNotFoundException("患者不存在");
        }

        LocalDate end = StringUtils.hasText(endDateStr)
                ? LocalDate.parse(endDateStr, DATE_FORMATTER)
                : LocalDate.now();
        LocalDate start = StringUtils.hasText(startDateStr)
                ? LocalDate.parse(startDateStr, DATE_FORMATTER)
                : end.minusDays(30);

        // 查询该区间内所有记录并按日期分组（数据量不大时足够）
        List<ProRecord> records = proRecordMapper
                .findByPatientIdAndRecordDateBetweenOrderByRecordDateDesc(
                        patientId, start, end, org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        Map<LocalDate, List<ProRecord>> byDate = records.stream()
                .collect(Collectors.groupingBy(ProRecord::getRecordDate));

        List<LocalDate> allDates = new ArrayList<>(byDate.keySet());
        allDates.sort(Comparator.reverseOrder());

        int p = page == null || page < 1 ? 0 : page - 1;
        int size = pageSize == null || pageSize <= 0 ? 30 : pageSize;
        int fromIndex = p * size;
        int toIndex = Math.min(fromIndex + size, allDates.size());
        if (fromIndex >= allDates.size()) {
            return ProHistoryPageDto.builder()
                    .list(new ArrayList<>())
                    .total((long) allDates.size())
                    .continuousCheckInDays(proRecordMapper.countContinuousCheckInDays(patientId))
                    .build();
        }

        List<LocalDate> pageDates = allDates.subList(fromIndex, toIndex);
        List<ProHistoryItemDto> items = new ArrayList<>();
        for (LocalDate date : pageDates) {
            List<ProRecord> list = byDate.getOrDefault(date, List.of());
            int totalScore = list.stream()
                    .map(ProRecord::getAnswerScore)
                    .filter(s -> s != null)
                    .mapToInt(Integer::intValue)
                    .sum();
            LocalDateTime createdAt = list.stream()
                    .map(ProRecord::getCreatedAt)
                    .filter(t -> t != null)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);

            List<ProHistoryAnswerDto> answers = list.stream()
                    .map(r -> ProHistoryAnswerDto.builder()
                            .questionId(r.getQuestionId())
                            .questionTitle(r.getQuestionTitle())
                            .answer(r.getAnswer())
                            .score(r.getAnswerScore())
                            .build())
                    .collect(Collectors.toList());

            items.add(ProHistoryItemDto.builder()
                    .recordDate(date.toString())
                    .totalScore(totalScore)
                    .answers(answers)
                    .createdAt(createdAt != null ? createdAt.toString() : null)
                    .build());
        }

        int continuous = proRecordMapper.countContinuousCheckInDays(patientId);

        return ProHistoryPageDto.builder()
                .list(items)
                .total((long) allDates.size())
                .continuousCheckInDays(continuous)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SymptomTrendResponseDto getSymptomTrend(String patientId, String questionId, Integer days) {
        if (!patientMapper.existsById(patientId)) {
            throw new EntityNotFoundException("患者不存在");
        }

        int d = days == null || days <= 0 ? 14 : days;
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(d - 1L);

        List<Object[]> rows = proRecordMapper.findScoreTrend(patientId, questionId, start, end);
        List<SymptomTrendPointDto> trend = new ArrayList<>();
        int sum = 0;
        int count = 0;
        Integer peakScore = null;
        LocalDate peakDate = null;

        for (Object[] row : rows) {
            LocalDate date = (LocalDate) row[0];
            Integer score = row[1] != null ? ((Number) row[1]).intValue() : null;
            if (score != null) {
                sum += score;
                count++;
                if (peakScore == null || score > peakScore) {
                    peakScore = score;
                    peakDate = date;
                }
            }
            trend.add(SymptomTrendPointDto.builder()
                    .date(date.toString())
                    .score(score)
                    .build());
        }

        Double avg = count > 0 ? (double) sum / count : 0.0;

        String questionTitle = proQuestionMapper.findById(questionId)
                .map(ProQuestion::getTitle)
                .orElse(null);

        return SymptomTrendResponseDto.builder()
                .questionTitle(questionTitle)
                .trend(trend)
                .avgScore(avg)
                .peakScore(peakScore)
                .peakDate(peakDate != null ? peakDate.toString() : null)
                .build();
    }

    private void updatePsychEnergy(Patient patient, int delta, String triggerType, String sourceRef) {
        int before = patient.getPsychEnergy() != null ? patient.getPsychEnergy() : 0;
        int after = Math.max(0, Math.min(100, before + delta));
        // 更新冗余字段
        patientMapper.addPsychEnergy(patient.getId(), delta);

        PsychEnergyLog log = PsychEnergyLog.builder()
                .patientId(patient.getId())
                .logDate(LocalDate.now())
                .psychEnergy(after)
                .delta(delta)
                .triggerType(triggerType)
                .sourceRef(sourceRef)
                .build();
        psychEnergyLogMapper.save(log);
    }

    private void addHopeTreeExp(Patient patient, int exp, String source, String sourceRefId) {
        HopeTreeProgress progress = hopeTreeProgressMapper.findByPatientId(patient.getId())
                .orElse(null);
        int levelBefore = progress != null && progress.getCurrentLevel() != null
                ? progress.getCurrentLevel()
                : 1;
        hopeTreeProgressMapper.addExp(patient.getId(), exp, LocalDateTime.now());

        HopeTreeGrowthHistory history = HopeTreeGrowthHistory.builder()
                .patientId(patient.getId())
                .growthSource(source)
                .expAmount(exp)
                .levelBefore(levelBefore)
                .levelAfter(levelBefore)
                .levelUp(false)
                .sourceRefId(sourceRefId)
                .build();
        hopeTreeGrowthHistoryMapper.save(history);
    }
}

