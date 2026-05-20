package com.xinya.business.hopetree.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("hope_tree_growth_history")
public class HopeTreeGrowthHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String patientId;
    /** check_in | education | conversation | stage_advance | meditation */
    private String growthSource;
    private Integer expAmount;
    private Integer levelBefore;
    private Integer levelAfter;
    @Builder.Default
    private Boolean levelUp = false;
    private String sourceRefId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
