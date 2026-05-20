package com.xinya.business.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse {
    private String reply;
    private String sessionId;
    private int psychEnergyDelta;
    private List<String> recommendedQuestions;
    private boolean crisisAlert;
    private int hopeTreeExpDelta;
}
