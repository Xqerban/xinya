package com.xinya.dtx.hopetree.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "hope_tree_progress")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HopeTreeProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "patient_id", length = 36, nullable = false, unique = true)
    private String patientId;

    @Column(name = "current_level", nullable = false)
    @Builder.Default
    private Integer currentLevel = 1;

    @Column(name = "current_exp", nullable = false)
    @Builder.Default
    private Integer currentExp = 0;

    @Column(name = "total_exp", nullable = false)
    @Builder.Default
    private Integer totalExp = 0;

    @Column(name = "next_level_exp", nullable = false)
    @Builder.Default
    private Integer nextLevelExp = 100;

    @Column(name = "total_growth_days", nullable = false)
    @Builder.Default
    private Integer totalGrowthDays = 0;

    @Column(name = "last_growth_date")
    private LocalDateTime lastGrowthDate;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
