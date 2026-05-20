package com.xinya.business.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("crisis_keywords")
public class CrisisKeyword {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String keyword;
    /** warning | critical */
    private String crisisLevel;
    @Builder.Default
    private Boolean isActive = true;
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
