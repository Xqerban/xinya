package com.xinya.business.pro.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pro_questions")
public class ProQuestion {

    @TableId(type = IdType.INPUT)
    private String id;

    private String stage;
    private String title;
    /** single_choice | scale | multi_choice */
    private String type;
    private String options;
    private Integer scaleMin;
    private Integer scaleMax;
    private String minLabel;
    private String maxLabel;
    private String symptomKey;
    @Builder.Default
    private Integer sortOrder = 0;
    @Builder.Default
    private Boolean isActive = true;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
