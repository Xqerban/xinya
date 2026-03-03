package com.xinya.dtx.system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pro_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_patient_date_question",
                columnNames = {"patient_id", "record_date", "question_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "question_id", length = 50, nullable = false)
    private String questionId;

    @Column(name = "question_title", length = 200)
    private String questionTitle;

    @Column(name = "answer", length = 200, nullable = false)
    private String answer;

    @Column(name = "answer_score", nullable = false)
    @Builder.Default
    private Integer answerScore = 0;

    @Column(name = "symptom_key", length = 50)
    private String symptomKey;

    @Column(name = "client_timestamp")
    private Long clientTimestamp;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
