package com.xinya.dtx.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对话记录实体
 */
@Entity
@Table(name = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "patient_id", nullable = false, length = 36)
    private String patientId;
    
    @Column(name = "agent_type", nullable = false, length = 10)
    private String agentType;  // "psych" | "nurse"
    
    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "is_from_user")
    private Boolean isFromUser;
    
    @Column(name = "psych_energy_delta")
    private Integer psychEnergyDelta = 0;
    
    @Column(name = "crisis_alert")
    private Boolean crisisAlert = false;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
