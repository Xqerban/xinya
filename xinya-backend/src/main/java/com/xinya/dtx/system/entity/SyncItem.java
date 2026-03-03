package com.xinya.dtx.system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sync_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 客户端唯一ID，用于幂等去重 */
    @Column(name = "client_id", length = 100, nullable = false, unique = true)
    private String clientId;

    @Column(name = "device_id", length = 100, nullable = false)
    private String deviceId;

    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    /** pro_submit | agent_chat | hopetree_grow | education_progress | robot_data */
    @Column(name = "item_type", length = 50, nullable = false)
    private String itemType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    /** pending | success | failed */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "pending";

    @Column(name = "server_id", length = 100)
    private String serverId;

    @Column(name = "error_code")
    private Integer errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "client_created_at")
    private Long clientCreatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
