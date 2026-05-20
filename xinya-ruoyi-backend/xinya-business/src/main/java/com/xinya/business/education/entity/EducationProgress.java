package com.xinya.business.education.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("education_progress")
public class EducationProgress {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String patientId;
    private String contentId;
    @Builder.Default
    private Integer watchedSeconds = 0;
    @Builder.Default
    private Boolean completed = false;
    @Builder.Default
    private Boolean rewardGiven = false;
    private LocalDateTime lastWatchedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
