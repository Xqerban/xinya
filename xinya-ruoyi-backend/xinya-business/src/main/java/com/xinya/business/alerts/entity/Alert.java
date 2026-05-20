package com.xinya.business.alerts.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("alerts")
public class Alert {

    @TableId(type = IdType.INPUT)
    private String id;

    private String patientId;
    private String patientName;
    /** crisis | symptom | blood | manual */
    private String alertType;
    /** info | warning | critical */
    private String level;
    private String message;
    private String triggerMessage;
    @Builder.Default
    private Boolean resolved = false;
    private String resolvedBy;
    private String resolvedNote;
    private LocalDateTime resolvedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
