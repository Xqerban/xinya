package com.xinya.dtx.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 希望之树进度实体
 */
@Entity
@Table(name = "hope_tree_progress")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HopeTreeProgress {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "patient_id", nullable = false, unique = true, length = 36)
    private String patientId;
    
    @Column(name = "current_level")
    private Integer currentLevel = 1;
    
    @Column(name = "current_exp")
    private Integer currentExp = 0;
    
    @Column(name = "next_level_exp")
    private Integer nextLevelExp = 100;
    
    @Column(name = "total_growth_days")
    private Integer totalGrowthDays = 0;
    
    @Column(name = "last_growth_date")
    private LocalDateTime lastGrowthDate;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
