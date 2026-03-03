package com.xinya.dtx.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentChatRequest {

    @NotBlank
    private String patientId;

    @NotBlank
    private String agentType; // psych | nurse

    @NotBlank
    private String message;

    private String sessionId;

    private Long clientTimestamp;
}

