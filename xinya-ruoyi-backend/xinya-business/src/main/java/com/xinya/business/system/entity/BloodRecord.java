package com.xinya.business.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("blood_records")
public class BloodRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String patientId;
    private LocalDate recordDate;
    private BigDecimal wbc;
    private BigDecimal neutrophil;
    private BigDecimal platelet;
    private BigDecimal hemoglobin;
    private String wbcTrend;
    private String neutrophilTrend;
    private String plateletTrend;
    private String hemoglobinTrend;
    private String recordedBy;
    private String recordedByName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
