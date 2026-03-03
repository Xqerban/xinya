package com.xinya.dtx.agent.dto;

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

    private Integer psychEnergyDelta;

    private List<String> recommendedQuestions;

    private Boolean crisisAlert;

    private Integer hopeTreeExpDelta;
}

