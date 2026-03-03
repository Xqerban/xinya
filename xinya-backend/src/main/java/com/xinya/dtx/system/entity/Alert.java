package com.xinya.dtx.system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    @Column(name = "patient_name", length = 100)
    private String patientName;

    /** crisis | symptom | blood | manual */
    @Column(name = "alert_type", length = 30, nullable = false)
    private String alertType;

    /** info | warning | critical */
    @Column(name = "level", length = 20, nullable = false)
    private String level;

    @Column(name = "message", length = 1000, nullable = false)
    private String message;

    @Column(name = "trigger_message", length = 1000)
    private String triggerMessage;

    @Column(name = "resolved", nullable = false)
    @Builder.Default
    private Boolean resolved = false;

    @Column(name = "resolved_by", length = 36)
    private String resolvedBy;

    @Column(name = "resolved_note", length = 500)
    private String resolvedNote;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
