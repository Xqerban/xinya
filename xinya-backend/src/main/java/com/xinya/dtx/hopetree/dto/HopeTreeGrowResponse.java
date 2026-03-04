package com.xinya.dtx.hopetree.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HopeTreeGrowResponse {

    private Boolean success;

    private Integer newLevel;

    private Integer newExp;

    private Boolean levelUp;

    /** 升级时返回动画指令，如 "LEVEL_UP_TO_4" */
    private String levelUpAnimation;
}
