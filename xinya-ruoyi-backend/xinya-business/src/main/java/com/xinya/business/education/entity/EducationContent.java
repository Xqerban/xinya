package com.xinya.business.education.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("education_contents")
public class EducationContent {

    @TableId(type = IdType.INPUT)
    private String id;

    private String title;
    private String stage;
    private String category;
    private String description;
    /** video | article */
    private String contentType;
    private Integer durationSeconds;
    private String thumbnailUrl;
    private String mediaUrl;
    private String tags;
    @Builder.Default
    private Integer sortOrder = 0;
    @Builder.Default
    private Boolean isActive = true;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
