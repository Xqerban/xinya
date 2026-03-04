package com.xinya.dtx.dashboard.service.impl;

import com.xinya.dtx.dashboard.dto.AlertItemDto;
import com.xinya.dtx.dashboard.dto.DashboardDto;
import com.xinya.dtx.dashboard.dto.LearningStatsDto;
import com.xinya.dtx.dashboard.dto.PatientReportDto;
import com.xinya.dtx.dashboard.dto.PsychDistributionDto;
import com.xinya.dtx.dashboard.dto.SymptomHeatmapDto;
import com.xinya.dtx.dashboard.dto.SymptomSeriesDto;
import com.xinya.dtx.dashboard.dto.SymptomTrendDto;
import com.xinya.dtx.dashboard.service.DashboardService;
import com.xinya.dtx.education.dto.PatientProgressSummaryDto;
import com.xinya.dtx.education.service.EducationService;
import com.xinya.dtx.patient.entity.Patient;
import com.xinya.dtx.patient.mapper.PatientMapper;
import com.xinya.dtx.pro.mapper.ProQuestionMapper;
import com.xinya.dtx.alerts.entity.Alert;
import com.xinya.dtx.hopetree.entity.HopeTreeProgress;
import com.xinya.dtx.alerts.mapper.AlertMapper;
import com.xinya.dtx.education.mapper.EducationContentMapper;
import com.xinya.dtx.education.mapper.EducationProgressMapper;
import com.xinya.dtx.hopetree.mapper.HopeTreeGrowthHistoryMapper;
import com.xinya.dtx.hopetree.mapper.HopeTreeProgressMapper;
import com.xinya.dtx.pro.mapper.ProRecordMapper;
import com.xinya.dtx.patient.mapper.PsychEnergyLogMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final PatientMapper patientMapper;
    private final ProRecordMapper proRecordMapper;
    private final ProQuestionMapper proQuestionMapper;
    private final AlertMapper alertMapper;
    private final EducationProgressMapper educationProgressMapper;
    private final EducationContentMapper educationContentMapper;
    private final HopeTreeProgressMapper hopeTreeProgressMapper;
    private final HopeTreeGrowthHistoryMapper hopeTreeGrowthHistoryMapper;
    private final PsychEnergyLogMapper psychEnergyLogMapper;
    private final EducationService educationService;

    @Override
    @Transactional(readOnly = true)
    public DashboardDto getOverview() {
        long totalPatients = patientMapper.count();

        Map<String, Long> patientsByStage = new HashMap<>();
        patientMapper.countGroupByStage().forEach(row -> {
            Object stage = row.get("stage");
            Object count = row.get("count");
            if (stage != null && count != null) {
                patientsByStage.put(stage.toString(), ((Number) count).longValue());
            }
        });

        Double avgPsych = patientMapper.avgPsychEnergy();
        long lowEnergyCount = patientMapper.countByPsychEnergyLessThan(40);
        long todayCheckIn = patientMapper.countTodayCheckIn();
        double checkInRate = totalPatients > 0 ? (double) todayCheckIn / totalPatients : 0.0;

        List<SymptomTrendDto> symptomTrends = buildSymptomTrends();

        List<Alert> latestAlerts = alertMapper.findLatestUnresolved(PageRequest.of(0, 10));
        List<AlertItemDto> alertDtos = latestAlerts.stream()
                .map(a -> AlertItemDto.builder()
                        .id(a.getId())
                        .level(a.getLevel())
                        .message(a.getMessage())
                        .patientId(a.getPatientId())
                        .patientName(a.getPatientName())
                        .createdAt(a.getCreatedAt() != null ? a.getCreatedAt().toString() : null)
                        .resolved(a.getResolved())
                        .build())
                .collect(Collectors.toList());

        LearningStatsDto learningStats = buildLearningStats();

        return DashboardDto.builder()
                .totalPatients(totalPatients)
                .patientsByStage(patientsByStage)
                .avgPsychEnergy(avgPsych != null ? avgPsych : 0.0)
                .lowEnergyCount(lowEnergyCount)
                .todayCheckInCount(todayCheckIn)
                .checkInRate(checkInRate)
                .symptomTrends(symptomTrends)
                .alerts(alertDtos)
                .learningStats(learningStats)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PsychDistributionDto getPsychDistribution() {
        long total = patientMapper.count();
        long warning = patientMapper.countByPsychEnergyLessThan(40);
        long below60 = patientMapper.countByPsychEnergyLessThan(60);
        long mild = below60 - warning;
        long healthy = Math.max(0, total - warning - mild);

        double totalD = total > 0 ? total : 1.0;
        PsychDistributionDto.Bucket healthyBucket = PsychDistributionDto.Bucket.builder()
                .count(healthy)
                .range("60-100")
                .percent(healthy / totalD)
                .build();
        PsychDistributionDto.Bucket mildBucket = PsychDistributionDto.Bucket.builder()
                .count(mild)
                .range("40-59")
                .percent(mild / totalD)
                .build();
        PsychDistributionDto.Bucket warningBucket = PsychDistributionDto.Bucket.builder()
                .count(warning)
                .range("0-39")
                .percent(warning / totalD)
                .build();

        return PsychDistributionDto.builder()
                .healthy(healthyBucket)
                .mild(mildBucket)
                .warning(warningBucket)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SymptomHeatmapDto getSymptomHeatmap(Integer days) {
        int d = days == null || days <= 0 ? 7 : days;
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(d - 1L);

        List<String> dateStrings = new ArrayList<>();
        Map<String, List<Integer>> symptomScores = new HashMap<>();

        LocalDate current = start;
        while (!current.isAfter(end)) {
            dateStrings.add(current.toString());
            List<Object[]> rows = proRecordMapper.sumSymptomScoreByDate(current);
            Set<String> seenToday = new HashSet<>();
            for (Object[] row : rows) {
                String key = row[0] != null ? row[0].toString() : null;
                if (key == null) continue;
                int score = row[1] != null ? ((Number) row[1]).intValue() : 0;
                symptomScores.computeIfAbsent(key, k -> new ArrayList<>());
                seenToday.add(key);
                symptomScores.get(key).add(score);
            }
            // 对于未出现的症状补 0
            for (Map.Entry<String, List<Integer>> e : symptomScores.entrySet()) {
                if (!seenToday.contains(e.getKey())) {
                    e.getValue().add(0);
                }
            }
            current = current.plusDays(1);
        }

        List<SymptomSeriesDto> series = symptomScores.entrySet().stream()
                .map(e -> SymptomSeriesDto.builder()
                        .name(e.getKey())
                        .scores(e.getValue())
                        .build())
                .collect(Collectors.toList());

        return SymptomHeatmapDto.builder()
                .dates(dateStrings)
                .symptoms(series)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PatientReportDto getPatientReport(String patientId) {
        Patient patient = patientMapper.findById(patientId)
                .orElseThrow(() -> new EntityNotFoundException("患者不存在"));

        LocalDate today = LocalDate.now();
        LocalDate admissionDate = patient.getAdmissionDate();
        int totalDays = admissionDate != null
                ? (int) Math.max(1, ChronoUnit.DAYS.between(admissionDate, today) + 1)
                : 0;

        int checkInDays = (int) proRecordMapper.countDistinctDateByPatientId(patientId);
        double checkInRate = totalDays > 0 ? (double) checkInDays / totalDays : 0.0;

        // 心理能量进展
        int currentPsych = patient.getPsychEnergy() != null ? patient.getPsychEnergy() : 0;
        LocalDate energyStart = admissionDate != null ? admissionDate : today.minusDays(30);
        Double avgEnergy = psychEnergyLogMapper.avgEnergyBetween(patientId, energyStart, today);
        Integer peakEnergy = psychEnergyLogMapper.maxEnergyBetween(patientId, energyStart, today);
        Map<String, Object> psychEnergyProgress = new HashMap<>();
        psychEnergyProgress.put("initial", 50); // 以 50 作为初始默认值
        psychEnergyProgress.put("current", currentPsych);
        psychEnergyProgress.put("peak", peakEnergy != null ? peakEnergy : currentPsych);
        psychEnergyProgress.put("avg", avgEnergy != null ? avgEnergy : (double) currentPsych);

        // 希望之树
        HopeTreeProgress progress = hopeTreeProgressMapper.findByPatientId(patientId).orElse(null);
        int level = progress != null && progress.getCurrentLevel() != null ? progress.getCurrentLevel() : 1;
        int totalExp = hopeTreeGrowthHistoryMapper.sumTotalExpByPatientId(patientId);
        Map<String, Object> hopeTree = new HashMap<>();
        hopeTree.put("level", level);
        hopeTree.put("totalExpGained", totalExp);

        // 宣教进度（复用 EducationService）
        PatientProgressSummaryDto eduSummary = educationService.getPatientProgress(patientId);
        Map<String, Object> education = new HashMap<>();
        education.put("completionRate", eduSummary.getCompletionRate());
        education.put("totalWatchMinutes", eduSummary.getTotalWatchedSeconds() / 60.0);

        // 症状摘要：此处先返回空列表，后续可以根据需要补充
        List<Map<String, Object>> symptomSummary = Collections.emptyList();

        return PatientReportDto.builder()
                .patientId(patient.getId())
                .patientName(patient.getName())
                .admissionDate(admissionDate != null ? admissionDate.toString() : null)
                .currentStage(patient.getStage())
                .totalDays(totalDays)
                .checkInDays(checkInDays)
                .checkInRate(checkInRate)
                .psychEnergyProgress(psychEnergyProgress)
                .hopeTreeProgress(hopeTree)
                .educationProgress(education)
                .symptomSummary(symptomSummary)
                .generatedAt(LocalDateTime.now().toString())
                .build();
    }

    private List<SymptomTrendDto> buildSymptomTrends() {
        // 取最近两周，对比上周 vs 本周的症状总分
        LocalDate end = LocalDate.now();
        LocalDate thisWeekStart = end.minusDays(6);
        LocalDate lastWeekStart = thisWeekStart.minusDays(7);
        LocalDate lastWeekEnd = thisWeekStart.minusDays(1);

        Map<String, Integer> thisWeekMap = sumSymptomRange(thisWeekStart, end);
        Map<String, Integer> lastWeekMap = sumSymptomRange(lastWeekStart, lastWeekEnd);

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(thisWeekMap.keySet());
        allKeys.addAll(lastWeekMap.keySet());

        List<SymptomTrendDto> list = new ArrayList<>();
        for (String key : allKeys) {
            int lastWeek = lastWeekMap.getOrDefault(key, 0);
            int thisWeek = thisWeekMap.getOrDefault(key, 0);
            double changePercent;
            if (lastWeek == 0) {
                changePercent = thisWeek > 0 ? 100.0 : 0.0;
            } else {
                changePercent = ((double) (thisWeek - lastWeek) / lastWeek) * 100.0;
            }
            String trend;
            if (thisWeek > lastWeek) {
                trend = "UP";
            } else if (thisWeek < lastWeek) {
                trend = "DOWN";
            } else {
                trend = "FLAT";
            }
            list.add(SymptomTrendDto.builder()
                    .symptom(key)
                    .lastWeek(lastWeek)
                    .thisWeek(thisWeek)
                    .changePercent(changePercent)
                    .trend(trend)
                    .build());
        }
        return list;
    }

    private Map<String, Integer> sumSymptomRange(LocalDate start, LocalDate end) {
        Map<String, Integer> map = new HashMap<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            List<Object[]> rows = proRecordMapper.sumSymptomScoreByDate(current);
            for (Object[] row : rows) {
                String key = row[0] != null ? row[0].toString() : null;
                if (key == null) continue;
                int score = row[1] != null ? ((Number) row[1]).intValue() : 0;
                map.merge(key, score, Integer::sum);
            }
            current = current.plusDays(1);
        }
        return map;
    }

    private LearningStatsDto buildLearningStats() {
        Double avgCompletion = educationProgressMapper.avgCompletionRate();
        Double avgWatchSeconds = educationProgressMapper.avgWatchSeconds();
        double avgMinutes = avgWatchSeconds != null ? avgWatchSeconds / 60.0 : 0.0;
        double engagement = avgCompletion != null ? avgCompletion : 0.0;
        return LearningStatsDto.builder()
                .avgCompletionRate(avgCompletion != null ? avgCompletion : 0.0)
                .avgWatchTimeMinutes(avgMinutes)
                .engagementRate(engagement)
                .build();
    }
}

