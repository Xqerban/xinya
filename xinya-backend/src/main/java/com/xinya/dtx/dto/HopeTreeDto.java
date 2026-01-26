package com.xinya.dtx.dto;

import com.xinya.dtx.entity.HopeTreeProgress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HopeTreeDto {
    private Integer currentLevel;
    private Integer currentExp;
    private Integer nextLevelExp;
    private Integer totalGrowthDays;
    
    public static HopeTreeDto fromEntity(HopeTreeProgress progress) {
        return HopeTreeDto.builder()
            .currentLevel(progress.getCurrentLevel())
            .currentExp(progress.getCurrentExp())
            .nextLevelExp(progress.getNextLevelExp())
            .totalGrowthDays(progress.getTotalGrowthDays())
            .build();
    }
    
    public static HopeTreeDto defaultTree() {
        return HopeTreeDto.builder()
            .currentLevel(1)
            .currentExp(0)
            .nextLevelExp(100)
            .totalGrowthDays(0)
            .build();
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class HopeTreeGrowRequest {
    private String patientId;
    private String growthSource;  // "check_in" | "education" | "conversation"
    private Integer expAmount;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class HopeTreeGrowResponse {
    private Boolean success;
    private Integer newLevel;
    private Integer newExp;
    private Boolean levelUp;
}
