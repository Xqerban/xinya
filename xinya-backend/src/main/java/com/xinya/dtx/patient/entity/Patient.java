package com.xinya.dtx.patient.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "patients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Patient {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "age")
    private Integer age;

    /** MALE | FEMALE */
    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "diagnosis", length = 200)
    private String diagnosis;

    /** ADMISSION | PRETREATMENT | TRANSPLANT | REBUILD | DISCHARGE */
    @Column(name = "stage", length = 20, nullable = false)
    @Builder.Default
    private String stage = "ADMISSION";

    @Column(name = "stage_start_date", nullable = false)
    private LocalDate stageStartDate;

    @Column(name = "psych_energy", nullable = false)
    @Builder.Default
    private Integer psychEnergy = 50;

    @Column(name = "tree_level", nullable = false)
    @Builder.Default
    private Integer treeLevel = 1;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Column(name = "room_number", length = 20)
    private String roomNumber;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
