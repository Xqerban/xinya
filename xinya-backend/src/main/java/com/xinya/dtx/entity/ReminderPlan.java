package com.xinya.dtx.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reminder_plans",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_patient_plan_reminder",
                columnNames = {"patient_id", "plan_date", "reminder_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    /** Agent 生成的 reminderId，用于去重 */
    @Column(name = "reminder_id", length = 50, nullable = false)
    private String reminderId;

    @Column(name = "scheduled_time", length = 10, nullable = false)
    private String scheduledTime;

    /** education_push | encouragement | medication_reminder */
    @Column(name = "type", length = 30, nullable = false)
    private String type;

    @Column(name = "content_id", length = 36)
    private String contentId;

    @Column(name = "push_message", columnDefinition = "TEXT", nullable = false)
    private String pushMessage;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 1;

    /** pending | sent | completed | skipped */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "pending";

    @Column(name = "trigger_reason", length = 500)
    private String triggerReason;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
