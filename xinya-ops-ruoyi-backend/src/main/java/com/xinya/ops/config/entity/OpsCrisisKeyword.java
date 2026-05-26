package com.xinya.ops.config.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@TableName("crisis_keywords")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsCrisisKeyword {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("keyword")
    private String keyword;

    /** warning | critical */
    @TableField("crisis_level")
    private String crisisLevel;

    @Builder.Default
    @TableField("is_active")
    private Boolean isActive = true;

    @TableField("created_by")
    private String createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
