package com.xinya.dtx.system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "hope_tree_growth_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HopeTreeGrowthHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    /** check_in | education | conversation | stage_advance | meditation */
    @Column(name = "growth_source", length = 30, nullable = false)
    private String growthSource;

    @Column(name = "exp_amount", nullable = false)
    private Integer expAmount;

    @Column(name = "level_before", nullable = false)
    private Integer levelBefore;

    @Column(name = "level_after", nullable = false)
    private Integer levelAfter;

    @Column(name = "level_up", nullable = false)
    @Builder.Default
    private Boolean levelUp = false;

    @Column(name = "source_ref_id", length = 100)
    private String sourceRefId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
