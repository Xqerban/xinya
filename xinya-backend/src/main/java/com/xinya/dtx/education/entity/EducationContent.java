package com.xinya.dtx.education.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "education_contents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationContent {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /** 适用临床阶段，null=全阶段 */
    @Column(name = "stage", length = 20)
    private String stage;

    @Column(name = "category", length = 50, nullable = false)
    private String category;

    @Column(name = "description", length = 500)
    private String description;

    /** video | article */
    @Column(name = "content_type", length = 20, nullable = false)
    private String contentType;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    /** 逗号分隔的标签 */
    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
