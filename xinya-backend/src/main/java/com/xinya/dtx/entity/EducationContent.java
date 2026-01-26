package com.xinya.dtx.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 宣教内容实体
 */
@Entity
@Table(name = "education_contents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationContent {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @Column(nullable = false, length = 200)
    private String title;
    
    @Column(nullable = false, length = 50)
    private String category;
    
    @Column(length = 500)
    private String description;
    
    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;  // "video" | "article"
    
    @Column(name = "duration_seconds")
    private Integer durationSeconds;
    
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;
    
    @Column(name = "media_url", length = 500)
    private String mediaUrl;
    
    @Column(length = 500)
    private String tags;  // 逗号分隔的标签
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
