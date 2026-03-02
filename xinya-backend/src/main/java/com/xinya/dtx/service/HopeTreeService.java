package com.xinya.dtx.service;

import com.xinya.dtx.dto.HopeTreeDto;
import com.xinya.dtx.entity.HopeTreeProgress;
import com.xinya.dtx.repository.HopeTreeRepository;
import com.xinya.dtx.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class HopeTreeService {
    
    private final HopeTreeRepository hopeTreeRepository;
    private final PatientRepository patientRepository;
    
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
        // 达到最高等级(7)时 nextLevelExp 设为0，表示无需继续升级
        progress.setNextLevelExp(newLevel >= 7 ? 0 : LEVEL_EXP[newLevel]);
        progress.setLastGrowthDate(LocalDateTime.now());
        
        hopeTreeRepository.save(progress);

        // 同步更新 Patient.treeLevel，确保两处数据一致
        if (newLevel > oldLevel) {
            int finalNewLevel = newLevel;
            patientRepository.findById(patientId).ifPresent(patient -> {
                patient.setTreeLevel(finalNewLevel);
                patientRepository.save(patient);
            });
        }
        
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
