package com.xinya.dtx.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 小护士 - 护理宣教智能体客户端
 * 基于RAG的护理知识问答
 */
@Component
@RequiredArgsConstructor
public class NurseAgentClient {
    
    private final AiGateway aiGateway;
    
    private static final String SYSTEM_PROMPT = """
        你是"小护士"，一个专业、耐心的护理宣教AI助手，专为骨髓移植隔离病房的患者设计。
        
        你的核心职责：
        1. 知识解答：回答患者关于治疗、护理、饮食等方面的问题
        2. 症状指导：根据患者描述的症状，提供初步的护理建议
        3. 宣教推送：主动推荐相关的学习内容
        4. 提醒服务：根据患者所处阶段，给出护理提醒
        
        回答原则：
        - 使用通俗易懂的语言解释医学知识
        - 回答要准确、专业，但不过于复杂
        - 遇到需要医护人员判断的情况，提醒患者咨询医生
        - 适时推荐相关的视频学习内容
        - 回复控制在150字以内
        
        重要提醒：
        - 不要给出具体的用药建议
        - 紧急症状要提醒患者立即呼叫护士
        """;
    
    public Mono<String> chat(String userMessage, String patientContext) {
        String contextualPrompt = SYSTEM_PROMPT + "\n\n患者背景信息：" + patientContext;
        return aiGateway.chatCompletion(contextualPrompt, userMessage);
    }
    
    /**
     * 获取默认回复（AI未启用时使用）
     */
    public String getDefaultReply(String userMessage) {
        if (userMessage.contains("恶心") || userMessage.contains("呕吐")) {
            return "恶心和呕吐是预处理期常见的反应。建议您：1) 少食多餐，避免油腻食物；2) 可以含一片生姜缓解；3) 保持口腔清洁。如果症状严重，请及时告诉护士。我推荐您观看《预处理期护理要点》视频了解更多。";
        }
        if (userMessage.contains("感染") || userMessage.contains("发烧")) {
            return "预防感染非常重要！请注意：1) 勤洗手，保持手部卫生；2) 避免接触生食；3) 保持口腔清洁。如果出现发热（体温≥38℃），请立即呼叫护士。推荐观看《感染预防指南》视频。";
        }
        return "这是一个很好的问题。作为Demo，这里返回默认回复。实际接入AI后，我会为您提供更专业的护理知识解答。如有紧急情况，请及时呼叫护士。";
    }
}
