package com.xinya.business.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationItemDto {
    private Long id;
    private String sessionId;
    private String agentType;
    private String message;
    private Boolean isFromUser;
    private Integer psychEnergyDelta;
    private Boolean crisisAlert;
    private String createdAt;
}
