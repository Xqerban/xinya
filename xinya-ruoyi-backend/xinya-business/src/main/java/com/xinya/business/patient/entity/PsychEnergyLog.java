package com.xinya.business.patient.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("psych_energy_log")
public class PsychEnergyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String patientId;
    private LocalDate logDate;
    private Integer psychEnergy;
    @Builder.Default
    private Integer delta = 0;
    /** pro_checkin | conversation | manual */
    private String triggerType;
    private String sourceRef;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
