package com.xinya.business.agent.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationDto {
    private String sessionId;
    private String patientId;
    private String agentType;
    private Integer messageCount;
    private String lastMessageAt;
}
