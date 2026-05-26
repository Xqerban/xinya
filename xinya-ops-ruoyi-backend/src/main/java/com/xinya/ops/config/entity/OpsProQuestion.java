package com.xinya.ops.config.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@TableName("pro_questions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsProQuestion {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("stage")
    private String stage;

    @TableField("title")
    private String title;

    @TableField("type")
    private String type;

    @TableField("options")
    private String options;

    @TableField("scale_min")
    private Integer scaleMin;

    @TableField("scale_max")
    private Integer scaleMax;

    @TableField("min_label")
    private String minLabel;

    @TableField("max_label")
    private String maxLabel;

    @TableField("symptom_key")
    private String symptomKey;

    @Builder.Default
    @TableField("sort_order")
    private Integer sortOrder = 0;

    @Builder.Default
    @TableField("is_active")
    private Boolean isActive = true;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
