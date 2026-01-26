package com.xinya.dtx.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PRO数据采集记录实体
 */
@Entity
@Table(name = "pro_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "patient_id", nullable = false, length = 36)
    private String patientId;
    
    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;
    
    @Column(name = "question_id", nullable = false, length = 50)
    private String questionId;
    
    @Column(name = "question_title", length = 200)
    private String questionTitle;
    
    @Column(nullable = false, length = 200)
    private String answer;
    
    @Column(name = "answer_score")
    private Integer answerScore = 0;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
