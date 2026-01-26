package com.xinya.dtx.service;

import com.xinya.dtx.dto.HopeTreeDto;
import com.xinya.dtx.entity.HopeTreeProgress;
import com.xinya.dtx.repository.HopeTreeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HopeTreeService {
    
    private final HopeTreeRepository hopeTreeRepository;
    
    // 每级升级所需经验值
    private static final int[] LEVEL_EXP = {0, 100, 250, 450, 700, 1000, 1400, Integer.MAX_VALUE};
    
    public HopeTreeDto getProgress(String patientId) {
        return hopeTreeRepository.findByPatientId(patientId)
            .map(HopeTreeDto::fromEntity)
            .orElse(HopeTreeDto.defaultTree());
    }
    
    @Transactional
    public GrowResult grow(String patientId, String source, int expAmount) {
        HopeTreeProgress progress = hopeTreeRepository.findByPatientId(patientId)
            .orElseGet(() -> {
                HopeTreeProgress newProgress = HopeTreeProgress.builder()
                    .patientId(patientId)
                    .currentLevel(1)
                    .currentExp(0)
                    .nextLevelExp(LEVEL_EXP[1])
                    .totalGrowthDays(0)
                    .build();
                return hopeTreeRepository.save(newProgress);
            });
        
        int oldLevel = progress.getCurrentLevel();
        int newExp = progress.getCurrentExp() + expAmount;
        int newLevel = oldLevel;
        
        // 检查是否升级
        while (newLevel < 7 && newExp >= LEVEL_EXP[newLevel]) {
            newExp -= LEVEL_EXP[newLevel];
            newLevel++;
        }
        
        // 更新进度
        progress.setCurrentExp(newExp);
        progress.setCurrentLevel(newLevel);
        progress.setNextLevelExp(LEVEL_EXP[Math.min(newLevel, 6)]);
        progress.setLastGrowthDate(LocalDateTime.now());
        
        hopeTreeRepository.save(progress);
        
        return new GrowResult(
            true,
            newLevel,
            newExp,
            newLevel > oldLevel
        );
    }
    
    public record GrowResult(
        boolean success,
        int newLevel,
        int newExp,
        boolean levelUp
    ) {}
}
