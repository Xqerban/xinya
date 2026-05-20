package com.xinya.business.hopetree.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HopeTreeGrowResponse {
    private boolean success;
    private int newLevel;
    private int newExp;
    private boolean levelUp;
    private String levelUpAnimation;
}
