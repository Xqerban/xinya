package com.xinya.dtx.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 患者实体
 */
@Entity
@Table(name = "patients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Patient {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClinicalStage stage;
    
    @Column(name = "psych_energy")
    private Integer psychEnergy = 50;
    
    @Column(name = "tree_level")
    private Integer treeLevel = 1;
    
    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;
    
    @Column(name = "room_number", length = 20)
    private String roomNumber;
    
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
