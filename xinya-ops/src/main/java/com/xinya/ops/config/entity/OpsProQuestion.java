package com.xinya.ops.config.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pro_questions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsProQuestion {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "stage", length = 20, nullable = false)
    private String stage;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "type", length = 30, nullable = false)
    private String type;

    @Column(name = "options", columnDefinition = "TEXT")
    private String options;

    @Column(name = "scale_min")
    private Integer scaleMin;

    @Column(name = "scale_max")
    private Integer scaleMax;

    @Column(name = "min_label", length = 50)
    private String minLabel;

    @Column(name = "max_label", length = 50)
    private String maxLabel;

    @Column(name = "symptom_key", length = 50)
    private String symptomKey;

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
