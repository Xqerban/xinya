package com.xinya.dtx.service;

import com.xinya.dtx.config.AiClientConfig;
import com.xinya.dtx.dto.AgentChatResponse;
import com.xinya.dtx.entity.Conversation;
import com.xinya.dtx.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {
    
    private final ConversationRepository conversationRepository;
    private final AiClientConfig aiClientConfig;
    private final PatientService patientService;
    
    @Transactional
    public AgentChatResponse chat(String patientId, String agentType, String message, String sessionId) {
        // 保存用户消息
        Conversation userMessage = Conversation.builder()
            .patientId(patientId)
            .agentType(agentType)
            .sessionId(sessionId)
            .message(message)
            .isFromUser(true)
            .build();
        conversationRepository.save(userMessage);
        
        // 生成回复（当前返回默认值，后续接入AI）
        AgentChatResponse response = generateResponse(patientId, agentType, message);
        
        // 保存AI回复
        Conversation aiReply = Conversation.builder()
            .patientId(patientId)
            .agentType(agentType)
            .sessionId(sessionId)
            .message(response.getReply())
            .isFromUser(false)
            .psychEnergyDelta(response.getPsychEnergyDelta())
            .crisisAlert(response.getCrisisAlert())
            .build();
        conversationRepository.save(aiReply);
        
        // 更新患者心理能量
        if (response.getPsychEnergyDelta() != 0) {
            patientService.updatePsychEnergy(patientId, response.getPsychEnergyDelta());
        }
        
        return response;
    }
    
    private AgentChatResponse generateResponse(String patientId, String agentType, String message) {
        if (!aiClientConfig.isEnabled()) {
            // AI未启用，返回默认回复
            return AgentChatResponse.defaultResponse(agentType);
        }
        
        // TODO: 调用实际的AI API
        // 这里预留接口，后续实现
        log.info("AI调用: patientId={}, agentType={}, message={}", patientId, agentType, message);
        return AgentChatResponse.defaultResponse(agentType);
    }
    
    public List<String> getRecommendedQuestions(String patientId, String agentType) {
        if ("psych".equals(agentType)) {
            return List.of(
                "今天心情怎么样？",
                "有什么让您感到担心的事情吗？",
                "想做一个放松练习吗？",
                "和我聊聊您的感受",
                "有什么好消息想分享吗？"
            );
        } else {
            return List.of(
                "预处理期需要注意什么？",
                "如何预防感染？",
                "饮食有什么禁忌？",
                "什么时候可以洗澡？",
                "出现恶心怎么办？"
            );
        }
    }
    
    /**
     * 检测是否存在危机干预信号
     */
    private boolean detectCrisisSignal(String message) {
        List<String> crisisKeywords = List.of(
            "不想活", "活着没意思", "自杀", "绝望", "放弃"
        );
        
        String lowerMessage = message.toLowerCase();
        return crisisKeywords.stream().anyMatch(lowerMessage::contains);
    }
}
