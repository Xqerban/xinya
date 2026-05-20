package com.xinya.business.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("reminder_plans")
public class ReminderPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String patientId;
    private LocalDate planDate;
    private String reminderId;
    private String scheduledTime;
    /** education_push | encouragement | medication_reminder */
    private String type;
    private String contentId;
    private String pushMessage;
    @Builder.Default
    private Integer priority = 1;
    /** pending | sent | completed | skipped */
    @Builder.Default
    private String status = "pending";
    private String triggerReason;
    private LocalDateTime sentAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
