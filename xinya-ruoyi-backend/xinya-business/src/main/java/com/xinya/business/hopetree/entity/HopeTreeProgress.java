package com.xinya.business.hopetree.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("hope_tree_progress")
public class HopeTreeProgress {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String patientId;
    @Builder.Default
    private Integer currentLevel = 1;
    @Builder.Default
    private Integer currentExp = 0;
    @Builder.Default
    private Integer totalExp = 0;
    @Builder.Default
    private Integer nextLevelExp = 100;
    @Builder.Default
    private Integer totalGrowthDays = 0;
    private LocalDateTime lastGrowthDate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
