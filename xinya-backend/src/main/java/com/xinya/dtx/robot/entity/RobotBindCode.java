package com.xinya.dtx.robot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "robot_bind_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotBindCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    @Column(name = "bind_code", length = 10, nullable = false)
    private String bindCode;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private Boolean used = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
