package com.xinya.dtx.system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    /** psych | nurse */
    @Column(name = "agent_type", length = 10, nullable = false)
    private String agentType;

    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /** true=用户消息，false=AI回复 */
    @Column(name = "is_from_user", nullable = false)
    private Boolean isFromUser;

    @Column(name = "psych_energy_delta", nullable = false)
    @Builder.Default
    private Integer psychEnergyDelta = 0;

    @Column(name = "hope_tree_exp_delta", nullable = false)
    @Builder.Default
    private Integer hopeTreeExpDelta = 0;

    @Column(name = "crisis_alert", nullable = false)
    @Builder.Default
    private Boolean crisisAlert = false;

    /** none | watch | warning | critical */
    @Column(name = "crisis_level", length = 20)
    private String crisisLevel;

    @Column(name = "crisis_keywords", length = 500)
    private String crisisKeywords;

    @Column(name = "emotion_signals", length = 500)
    private String emotionSignals;

    @Column(name = "client_timestamp")
    private Long clientTimestamp;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
