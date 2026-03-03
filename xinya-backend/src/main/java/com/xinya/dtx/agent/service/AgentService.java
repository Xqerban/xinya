package com.xinya.dtx.agent.service;

import com.xinya.dtx.agent.dto.AgentChatRequest;
import com.xinya.dtx.agent.dto.AgentChatResponse;
import com.xinya.dtx.agent.dto.ConversationItemDto;
import com.xinya.dtx.agent.dto.NursePushRequest;
import com.xinya.dtx.agent.dto.NursePushResponse;
import com.xinya.dtx.agent.dto.RecommendationsResponse;
import com.xinya.dtx.common.response.PageResult;

public interface AgentService {

    AgentChatResponse chat(AgentChatRequest request);

    RecommendationsResponse getRecommendations(String patientId, String agentType);

    PageResult<ConversationItemDto> getHistory(String patientId, String agentType, String sessionId,
                                               Integer page, Integer pageSize);

    NursePushResponse nursePush(NursePushRequest request);
}

