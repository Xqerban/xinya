package com.xinya.business.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinya.business.agent.dto.*;
import com.xinya.business.agent.entity.Conversation;
import com.xinya.business.agent.mapper.ConversationMapper;
import com.xinya.business.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final ConversationMapper conversationMapper;
    private final WebClient webClient;

    @Value("${xinya.ai.psych-base-url:http://localhost:8001}")
    private String psychBaseUrl;

    @Value("${xinya.ai.nurse-base-url:http://localhost:8443}")
    private String nurseBaseUrl;

    @Override
    public Flux<String> chat(AgentChatRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId() : UUID.randomUUID().toString();

        // 保存用户消息
        saveMessage(request.getPatientId(), sessionId, "psych", request.getMessage(), true);

        Map<String, Object> payload = Map.of(
                "patient_id", request.getPatientId(),
                "message", request.getMessage(),
                "conversation_id", sessionId
        );

        return webClient.post()
                .uri(psychBaseUrl + "/agent/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(chunk -> {
                    // 流式响应完成后可选持久化 AI 回复（仅处理 [DONE] 或汇总）
                })
                .onErrorResume(e -> Flux.just("data: {\"error\": \"" + e.getMessage() + "\"}\n\n"));
    }

    @Override
    public Flux<String> nurseChat(NurseChatRequest request) {
        String sessionId = request.getConversationId() != null
                ? request.getConversationId() : UUID.randomUUID().toString();

        Map<String, Object> payload = Map.of(
                "user_id", request.getUserId() != null ? request.getUserId() : "",
                "patient_id", request.getPatientId() != null ? request.getPatientId() : "",
                "message", request.getMessage(),
                "conversation_id", sessionId
        );

        return webClient.post()
                .uri(nurseBaseUrl + "/nurse/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(String.class)
                .onErrorResume(e -> Flux.just("data: {\"error\": \"" + e.getMessage() + "\"}\n\n"));
    }

    @Override
    public ConversationDto getOrCreateConversation(String patientId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getPatientId, patientId)
                .orderByDesc(Conversation::getCreatedAt)
                .last("LIMIT 1");
        Conversation latest = conversationMapper.selectOne(wrapper);

        String sessionId = (latest != null && latest.getSessionId() != null)
                ? latest.getSessionId() : UUID.randomUUID().toString();

        // 统计消息数
        LambdaQueryWrapper<Conversation> countWrapper = new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getPatientId, patientId)
                .eq(Conversation::getSessionId, sessionId);
        long msgCount = conversationMapper.selectCount(countWrapper);

        return ConversationDto.builder()
                .sessionId(sessionId)
                .patientId(patientId)
                .agentType("psych")
                .messageCount((int) msgCount)
                .lastMessageAt(latest != null && latest.getCreatedAt() != null
                        ? latest.getCreatedAt().toString() : null)
                .build();
    }

    @Override
    public void saveConversationMessage(String conversationId, String role, String content) {
        Conversation msg = Conversation.builder()
                .patientId(null)
                .sessionId(conversationId)
                .agentType("psych")
                .message(content)
                .isFromUser("user".equalsIgnoreCase(role))
                .build();
        conversationMapper.insert(msg);
    }

    private void saveMessage(String patientId, String sessionId, String agentType,
                              String message, boolean isFromUser) {
        try {
            Conversation conv = Conversation.builder()
                    .patientId(patientId)
                    .sessionId(sessionId)
                    .agentType(agentType)
                    .message(message)
                    .isFromUser(isFromUser)
                    .build();
            conversationMapper.insert(conv);
        } catch (Exception ignored) {
        }
    }
}
