package com.xinya.dtx.agent.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "crisis_keywords")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrisisKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "keyword", length = 100, nullable = false, unique = true)
    private String keyword;

    /** warning | critical */
    @Column(name = "crisis_level", length = 20, nullable = false)
    private String crisisLevel;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
