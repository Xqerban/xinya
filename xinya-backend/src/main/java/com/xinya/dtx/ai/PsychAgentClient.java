package com.xinya.dtx.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 小芽 - 心理陪护智能体客户端
 * 基于CBT(认知行为疗法)的对话策略
 */
@Component
@RequiredArgsConstructor
public class PsychAgentClient {
    
    private final AiGateway aiGateway;
    
    private static final String SYSTEM_PROMPT = """
        你是"小芽"，一个温暖、专业的心理陪护AI伙伴，专为骨髓移植隔离病房的患者设计。
        
        你的核心特质：
        1. 温暖共情：始终以温柔、理解的语气回应患者
        2. CBT导向：运用认知行为疗法的技巧帮助患者
        3. 正向引导：帮助患者看到希望，建立康复信心
        4. 危机敏感：识别患者的负面情绪，必要时进行干预
        
        对话原则：
        - 使用简洁、亲切的语言
        - 适时提供呼吸放松、正念等练习建议
        - 肯定患者的每一点进步
        - 避免使用医学术语
        - 回复控制在100字以内
        
        如果患者表达严重的负面情绪（如"不想活"、"绝望"等），请：
        1. 表达深切的理解和关心
        2. 引导患者进行简单的呼吸练习
        3. 建议患者与医护人员或家人交流
        """;
    
    public Mono<String> chat(String userMessage, String patientContext) {
        String contextualPrompt = SYSTEM_PROMPT + "\n\n患者背景信息：" + patientContext;
        return aiGateway.chatCompletion(contextualPrompt, userMessage);
    }
    
    /**
     * 获取默认回复（AI未启用时使用）
     */
    public String getDefaultReply(String userMessage) {
        if (containsNegativeKeywords(userMessage)) {
            return "我能感受到您现在的心情不太好。深呼吸一下，让我陪着您。治疗的路虽然辛苦，但您不是一个人在战斗。想和我聊聊发生了什么吗？";
        }
        return "我在这里陪着您。有什么想和我分享的吗？每一天都是新的开始，您已经很勇敢了。";
    }
    
    private boolean containsNegativeKeywords(String message) {
        String[] keywords = {"难受", "害怕", "担心", "焦虑", "睡不着", "不开心", "想哭"};
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
