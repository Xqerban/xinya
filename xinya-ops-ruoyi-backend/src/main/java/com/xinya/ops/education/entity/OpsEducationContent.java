package com.xinya.ops.education.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 宣教内容主控记录（存储于 xinya_ops.education_contents）
 * 发布后通过 ClinicalApiClient 同步到 xinya-ruoyi-backend
 */
@TableName("education_contents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsEducationContent {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("title")
    private String title;

    @TableField("stage")
    private String stage;

    @TableField("category")
    private String category;

    @TableField("description")
    private String description;

    @TableField("content_type")
    private String contentType;

    @TableField("duration_seconds")
    private Integer durationSeconds;

    @TableField("thumbnail_url")
    private String thumbnailUrl;

    @TableField("media_url")
    private String mediaUrl;

    @TableField("tags")
    private String tags;

    @Builder.Default
    @TableField("sort_order")
    private Integer sortOrder = 0;

    @Builder.Default
    @TableField("is_active")
    private Boolean isActive = true;

    @Builder.Default
    @TableField("synced_to_clinical")
    private Boolean syncedToClinical = false;

    @TableField("created_by")
    private String createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
