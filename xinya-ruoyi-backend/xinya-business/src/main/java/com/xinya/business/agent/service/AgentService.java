package com.xinya.business.agent.service;

import com.xinya.business.agent.dto.*;
import reactor.core.publisher.Flux;

public interface AgentService {
    Flux<String> chat(AgentChatRequest request);
    Flux<String> nurseChat(NurseChatRequest request);
    ConversationDto getOrCreateConversation(String patientId);
    void saveConversationMessage(String conversationId, String role, String content);
}
