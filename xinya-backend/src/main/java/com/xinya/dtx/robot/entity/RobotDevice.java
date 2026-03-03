package com.xinya.dtx.robot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "robot_devices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "device_id", length = 100, nullable = false, unique = true)
    private String deviceId;

    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    @Column(name = "device_token_hash", length = 255)
    private String deviceTokenHash;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    /** ONLINE | OFFLINE */
    @Column(name = "online_status", length = 20, nullable = false)
    @Builder.Default
    private String onlineStatus = "OFFLINE";

    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    /** WIFI | 4G | OFFLINE */
    @Column(name = "network_status", length = 20)
    private String networkStatus;

    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
