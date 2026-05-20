package com.xinya.business.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentChatRequest {
    @NotBlank
    private String patientId;
    private String agentType;
    private String sessionId;
    private String message;
    private Long clientTimestamp;
}
