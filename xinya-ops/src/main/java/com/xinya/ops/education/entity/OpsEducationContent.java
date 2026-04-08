package com.xinya.ops.education.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 宣教内容主控记录（存储于 xinya_ops.education_contents）
 * 发布后通过 ClinicalApiClient 同步到 xinya-backend
 */
@Entity
@Table(name = "education_contents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsEducationContent {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "stage", length = 20)
    private String stage;

    @Column(name = "category", length = 50, nullable = false)
    private String category;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "content_type", length = 20, nullable = false)
    private String contentType;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** 是否已同步到 xinya-backend */
    @Column(name = "synced_to_clinical", nullable = false)
    @Builder.Default
    private Boolean syncedToClinical = false;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
