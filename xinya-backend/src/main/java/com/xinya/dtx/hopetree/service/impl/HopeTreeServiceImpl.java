package com.xinya.dtx.hopetree.service.impl;

import com.xinya.dtx.common.response.PageResult;
import com.xinya.dtx.hopetree.dto.GrowthHistoryItemDto;
import com.xinya.dtx.hopetree.dto.HopeTreeGrowRequest;
import com.xinya.dtx.hopetree.dto.HopeTreeGrowResponse;
import com.xinya.dtx.hopetree.service.HopeTreeService;
import com.xinya.dtx.patient.dto.HopeTreeDto;
import com.xinya.dtx.patient.mapper.PatientMapper;
import com.xinya.dtx.hopetree.entity.HopeTreeGrowthHistory;
import com.xinya.dtx.hopetree.entity.HopeTreeProgress;
import com.xinya.dtx.hopetree.mapper.HopeTreeGrowthHistoryMapper;
import com.xinya.dtx.hopetree.mapper.HopeTreeProgressMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HopeTreeServiceImpl implements HopeTreeService {

    /** 升到下一级所需经验：index 0 = 1->2 需 100, 1 = 2->3 需 250, ..., 5 = 6->7 需 1400, 等级7为0 */
    private static final int[] NEXT_LEVEL_EXP = { 100, 250, 450, 700, 1000, 1400, 0 };

    private final PatientMapper patientMapper;
    private final HopeTreeProgressMapper hopeTreeProgressMapper;
    private final HopeTreeGrowthHistoryMapper hopeTreeGrowthHistoryMapper;

    @Override
    @Transactional(readOnly = true)
    public HopeTreeDto getState(String patientId) {
        if (!patientMapper.existsById(patientId)) {
            throw new EntityNotFoundException("患者不存在");
        }
        return hopeTreeProgressMapper.findByPatientId(patientId)
                .map(progress -> {
                    LocalDateTime lastGrowth = progress.getLastGrowthDate();
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
                .orElseGet(() -> HopeTreeDto.builder()
                        .currentLevel(1)
                        .currentExp(0)
                        .nextLevelExp(100)
                        .totalGrowthDays(0)
                        .levelName(resolveLevelName(1))
                        .levelImageUrl(resolveLevelImageUrl(1))
                        .todayExpGained(0)
                        .lastGrowthTime(null)
                        .build());
    }

    @Override
    @Transactional
    public HopeTreeGrowResponse grow(HopeTreeGrowRequest request) {
        String patientId = request.getPatientId();
        if (!patientMapper.existsById(patientId)) {
            throw new EntityNotFoundException("患者不存在");
        }

        HopeTreeProgress progress = hopeTreeProgressMapper.findByPatientId(patientId)
                .orElseGet(() -> {
                    HopeTreeProgress p = HopeTreeProgress.builder()
                            .patientId(patientId)
                            .build();
                    return hopeTreeProgressMapper.save(p);
                });

        int levelBefore = progress.getCurrentLevel();
        int expAmount = request.getExpAmount();
        LocalDateTime now = LocalDateTime.now();

        hopeTreeProgressMapper.addExp(patientId, expAmount, now);
        progress = hopeTreeProgressMapper.findByPatientId(patientId).orElseThrow();

        int level = progress.getCurrentLevel();
        int exp = progress.getCurrentExp();
        int nextExp = progress.getNextLevelExp();

        while (level < 7 && nextExp > 0 && exp >= nextExp) {
            exp -= nextExp;
            level++;
            nextExp = level < 7 ? NEXT_LEVEL_EXP[level - 1] : 0;
            hopeTreeProgressMapper.updateAfterLevelUp(patientId, level, exp, nextExp);
            patientMapper.updateTreeLevel(patientId, level);
        }

        boolean levelUp = level > levelBefore;
        String levelUpAnimation = levelUp ? "LEVEL_UP_TO_" + level : null;

        HopeTreeGrowthHistory history = HopeTreeGrowthHistory.builder()
                .patientId(patientId)
                .growthSource(request.getGrowthSource())
                .expAmount(expAmount)
                .levelBefore(levelBefore)
                .levelAfter(level)
                .levelUp(levelUp)
                .sourceRefId(null)
                .build();
        hopeTreeGrowthHistoryMapper.save(history);

        return HopeTreeGrowResponse.builder()
                .success(true)
                .newLevel(level)
                .newExp(exp)
                .levelUp(levelUp)
                .levelUpAnimation(levelUpAnimation)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<GrowthHistoryItemDto> getHistory(String patientId, Integer page, Integer pageSize) {
        if (!patientMapper.existsById(patientId)) {
            throw new EntityNotFoundException("患者不存在");
        }
        int p = page == null || page < 1 ? 0 : page - 1;
        int size = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        Pageable pageable = PageRequest.of(p, size);

        Page<HopeTreeGrowthHistory> historyPage =
                hopeTreeGrowthHistoryMapper.findByPatientIdOrderByCreatedAtDesc(patientId, pageable);
        List<GrowthHistoryItemDto> list = historyPage.getContent().stream()
                .map(h -> GrowthHistoryItemDto.builder()
                        .id(h.getId())
                        .growthSource(h.getGrowthSource())
                        .growthSourceName(resolveGrowthSourceName(h.getGrowthSource()))
                        .expAmount(h.getExpAmount())
                        .levelBefore(h.getLevelBefore())
                        .levelAfter(h.getLevelAfter())
                        .levelUp(Boolean.TRUE.equals(h.getLevelUp()))
                        .createdAt(h.getCreatedAt() != null ? h.getCreatedAt().toString() : null)
                        .build())
                .collect(Collectors.toList());

        return PageResult.<GrowthHistoryItemDto>builder()
                .list(list)
                .total(historyPage.getTotalElements())
                .page(p + 1)
                .pageSize(size)
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

    private String resolveLevelImageUrl(Integer level) {
        if (level == null) return null;
        return "/assets/hopetree/level" + level + ".png";
    }

    private String resolveGrowthSourceName(String source) {
        if (source == null) return null;
        return switch (source) {
            case "check_in" -> "每日打卡";
            case "education" -> "护理宣教";
            case "conversation" -> "情绪对话";
            case "stage_advance" -> "阶段流转";
            case "meditation" -> "冥想练习";
            default -> source;
        };
    }
}
