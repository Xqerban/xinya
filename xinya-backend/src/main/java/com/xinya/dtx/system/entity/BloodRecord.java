package com.xinya.dtx.system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "blood_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_patient_date",
                columnNames = {"patient_id", "record_date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloodRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    /** 白细胞 ×10⁹/L */
    @Column(name = "wbc", precision = 5, scale = 2)
    private BigDecimal wbc;

    /** 中性粒细胞 ×10⁹/L */
    @Column(name = "neutrophil", precision = 5, scale = 2)
    private BigDecimal neutrophil;

    /** 血小板 ×10⁹/L */
    @Column(name = "platelet", precision = 7, scale = 2)
    private BigDecimal platelet;

    /** 血红蛋白 g/L */
    @Column(name = "hemoglobin", precision = 6, scale = 2)
    private BigDecimal hemoglobin;

    /** RISING | FALLING | STABLE */
    @Column(name = "wbc_trend", length = 10)
    private String wbcTrend;

    @Column(name = "neutrophil_trend", length = 10)
    private String neutrophilTrend;

    @Column(name = "platelet_trend", length = 10)
    private String plateletTrend;

    @Column(name = "hemoglobin_trend", length = 10)
    private String hemoglobinTrend;

    @Column(name = "recorded_by", length = 36)
    private String recordedBy;

    @Column(name = "recorded_by_name", length = 100)
    private String recordedByName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
