package com.xinya.business.hopetree.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.business.hopetree.dto.HopeTreeDetailDto;
import com.xinya.business.hopetree.dto.HopeTreeGrowthHistoryDto;
import com.xinya.business.hopetree.entity.HopeTreeGrowthHistory;
import com.xinya.business.hopetree.entity.HopeTreeProgress;
import com.xinya.business.hopetree.mapper.HopeTreeGrowthHistoryMapper;
import com.xinya.business.hopetree.mapper.HopeTreeProgressMapper;
import com.xinya.business.hopetree.service.HopeTreeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HopeTreeServiceImpl implements HopeTreeService {

    private final HopeTreeProgressMapper progressMapper;
    private final HopeTreeGrowthHistoryMapper historyMapper;

    private static final int[] LEVEL_EXP = {0, 100, 250, 500, 1000, 2000, 5000};

    @Override
    public HopeTreeDetailDto getPatientTree(String patientId) {
        HopeTreeProgress progress = progressMapper.findByPatientId(patientId);
        if (progress == null) {
            progress = HopeTreeProgress.builder().patientId(patientId).build();
            progressMapper.insert(progress);
        }
        return toDetailDto(progress);
    }

    @Override
    @Transactional
    public void addGrowthExp(String patientId, int exp, String source, String note) {
        if (exp <= 0) return;
        HopeTreeProgress progress = progressMapper.findByPatientId(patientId);
        if (progress == null) {
            progress = HopeTreeProgress.builder().patientId(patientId).build();
            progressMapper.insert(progress);
        }
        int oldLevel = progress.getCurrentLevel() != null ? progress.getCurrentLevel() : 1;
        int newTotalExp = (progress.getTotalExp() != null ? progress.getTotalExp() : 0) + exp;
        int newCurrentExp = (progress.getCurrentExp() != null ? progress.getCurrentExp() : 0) + exp;
        int newLevel = resolveLevel(newTotalExp);
        int nextLvlExp = nextLevelExp(newLevel);

        progress.setCurrentExp(newCurrentExp);
        progress.setTotalExp(newTotalExp);
        progress.setCurrentLevel(newLevel);
        progress.setNextLevelExp(nextLvlExp);
        progress.setTotalGrowthDays(progress.getTotalGrowthDays() != null ? progress.getTotalGrowthDays() + 1 : 1);
        progress.setLastGrowthDate(LocalDateTime.now());
        progressMapper.updateById(progress);

        HopeTreeGrowthHistory history = HopeTreeGrowthHistory.builder()
                .patientId(patientId)
                .expAmount(exp)
                .growthSource(source)
                .levelBefore(oldLevel)
                .levelAfter(newLevel)
                .levelUp(newLevel > oldLevel)
                .build();
        historyMapper.insert(history);
    }

    @Override
    public List<HopeTreeGrowthHistoryDto> getGrowthHistory(String patientId, Integer limit) {
        int size = (limit == null || limit <= 0) ? 30 : limit;
        Page<HopeTreeGrowthHistory> page = new Page<>(1, size);
        historyMapper.findByPatientIdOrderByCreatedAtDesc(page, patientId);
        // Result loaded into page.getRecords()
        return page.getRecords().stream().map(this::toHistoryDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public HopeTreeDetailDto dailyCheckIn(String patientId) {
        HopeTreeProgress progress = progressMapper.findByPatientId(patientId);
        if (progress == null) {
            progress = HopeTreeProgress.builder().patientId(patientId).build();
            progressMapper.insert(progress);
        }
        if (progress.getLastGrowthDate() != null &&
                progress.getLastGrowthDate().toLocalDate().equals(LocalDate.now())) {
            return toDetailDto(progress);
        }
        addGrowthExp(patientId, 10, "DAILY_CHECK_IN", "每日打卡");
        HopeTreeProgress updated = progressMapper.findByPatientId(patientId);
        return toDetailDto(updated);
    }

    private int resolveLevel(int totalExp) {
        int level = 1;
        for (int i = LEVEL_EXP.length - 1; i >= 0; i--) {
            if (totalExp >= LEVEL_EXP[i]) {
                level = i + 1;
                break;
            }
        }
        return Math.min(level, 7);
    }

    private int nextLevelExp(int level) {
        if (level >= LEVEL_EXP.length) return Integer.MAX_VALUE;
        return LEVEL_EXP[level];
    }

    private HopeTreeDetailDto toDetailDto(HopeTreeProgress p) {
        return HopeTreeDetailDto.builder()
                .patientId(p.getPatientId())
                .currentLevel(p.getCurrentLevel())
                .currentExp(p.getCurrentExp())
                .nextLevelExp(p.getNextLevelExp())
                .totalGrowthDays(p.getTotalGrowthDays())
                .lastGrowthDate(p.getLastGrowthDate() != null ? p.getLastGrowthDate().toString() : null)
                .build();
    }

    private HopeTreeGrowthHistoryDto toHistoryDto(HopeTreeGrowthHistory h) {
        return HopeTreeGrowthHistoryDto.builder()
                .id(h.getId() != null ? h.getId().toString() : null)
                .patientId(h.getPatientId())
                .expGained(h.getExpAmount())
                .growthSource(h.getGrowthSource())
                .levelBefore(h.getLevelBefore())
                .levelAfter(h.getLevelAfter())
                .createdAt(h.getCreatedAt() != null ? h.getCreatedAt().toString() : null)
                .build();
    }
}
