package com.xinya.business.clinical.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("clinical_stage_history")
public class ClinicalStageHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String patientId;
    private String fromStage;
    private String toStage;
    private LocalDate transitionDate;
    @Builder.Default
    private Integer daysInStage = 0;
    private String operatorId;
    private String operatorName;
    private String operatorNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
