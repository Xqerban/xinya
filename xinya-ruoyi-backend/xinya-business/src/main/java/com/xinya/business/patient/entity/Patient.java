package com.xinya.business.patient.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("patients")
public class Patient {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;
    private Integer age;
    /** MALE | FEMALE */
    private String gender;
    private String diagnosis;
    /** ADMISSION | PRETREATMENT | TRANSPLANT | REBUILD | DISCHARGE */
    @Builder.Default
    private String stage = "ADMISSION";
    private LocalDate stageStartDate;
    @Builder.Default
    private Integer psychEnergy = 50;
    @Builder.Default
    private Integer treeLevel = 1;
    private LocalDate admissionDate;
    private String roomNumber;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
