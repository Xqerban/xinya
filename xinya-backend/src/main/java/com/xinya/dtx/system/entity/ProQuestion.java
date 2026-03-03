package com.xinya.dtx.system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pro_questions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProQuestion {

    /** 题目ID，如 q_nausea */
    @Id
    @Column(name = "id", length = 50)
    private String id;

    /** 适用临床阶段，ALL=全阶段 */
    @Column(name = "stage", length = 20, nullable = false)
    private String stage;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /** single_choice | scale | multi_choice */
    @Column(name = "type", length = 30, nullable = false)
    private String type;

    /** 选项 JSON，single_choice/multi_choice 用 */
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
}
