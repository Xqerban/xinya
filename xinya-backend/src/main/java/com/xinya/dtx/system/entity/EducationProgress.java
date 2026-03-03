package com.xinya.dtx.system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "education_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_patient_content",
                columnNames = {"patient_id", "content_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    @Column(name = "content_id", length = 36, nullable = false)
    private String contentId;

    @Column(name = "watched_seconds", nullable = false)
    @Builder.Default
    private Integer watchedSeconds = 0;

    @Column(name = "completed", nullable = false)
    @Builder.Default
    private Boolean completed = false;

    /** 防止重复发放希望之树奖励 */
    @Column(name = "reward_given", nullable = false)
    @Builder.Default
    private Boolean rewardGiven = false;

    @Column(name = "last_watched_at")
    private LocalDateTime lastWatchedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
