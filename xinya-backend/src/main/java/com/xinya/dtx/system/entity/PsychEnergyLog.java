package com.xinya.dtx.system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "psych_energy_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PsychEnergyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "psych_energy", nullable = false)
    private Integer psychEnergy;

    @Column(name = "delta", nullable = false)
    @Builder.Default
    private Integer delta = 0;

    /** pro_checkin | conversation | manual */
    @Column(name = "trigger_type", length = 30, nullable = false)
    private String triggerType;

    @Column(name = "source_ref", length = 100)
    private String sourceRef;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
