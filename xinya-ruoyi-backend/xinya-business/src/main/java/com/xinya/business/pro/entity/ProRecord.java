package com.xinya.business.pro.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pro_records")
public class ProRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String patientId;
    private LocalDate recordDate;
    private String questionId;
    private String questionTitle;
    private String answer;
    @Builder.Default
    private Integer answerScore = 0;
    private String symptomKey;
    private Long clientTimestamp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
