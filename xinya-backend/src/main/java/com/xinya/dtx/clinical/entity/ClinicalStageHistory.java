package com.xinya.dtx.clinical.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "clinical_stage_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    /** 来源阶段，首次入仓时为 null */
    @Column(name = "from_stage", length = 20)
    private String fromStage;

    @Column(name = "to_stage", length = 20, nullable = false)
    private String toStage;

    @Column(name = "transition_date", nullable = false)
    private LocalDate transitionDate;

    @Column(name = "days_in_stage", nullable = false)
    @Builder.Default
    private Integer daysInStage = 0;

    @Column(name = "operator_id", length = 36)
    private String operatorId;

    @Column(name = "operator_name", length = 100)
    private String operatorName;

    @Column(name = "operator_note", length = 500)
    private String operatorNote;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
