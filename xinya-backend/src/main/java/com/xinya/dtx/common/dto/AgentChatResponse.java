package com.xinya.dtx.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
class AgentChatRequest {
    private String patientId;
    private String agentType;  // "psych" | "nurse"
    private String message;
    private String sessionId;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse{
    private String reply;
    private Integer psychEnergyDelta;
    private List<String> recommendedQuestions;
    private Boolean crisisAlert;
    
    public static AgentChatResponse defaultResponse(String agentType) {
        List<String> questions;
        String reply;
        
        if ("psych".equals(agentType)) {
            reply = "我理解您的感受。作为Demo，这是默认回复。实际接入AI后将提供个性化的心理支持。";
            questions = List.of("今天心情怎么样？", "有什么让您担心的事吗？", "想做个放松练习吗？");
        } else {
            reply = "这是一个很好的问题。作为Demo，这是默认回复。实际接入AI后将提供专业的护理知识解答。";
            questions = List.of("预处理期需要注意什么？", "如何预防感染？", "饮食有什么禁忌？");
        }
        
        return AgentChatResponse.builder()
            .reply(reply)
            .psychEnergyDelta(5)
            .recommendedQuestions(questions)
            .crisisAlert(false)
            .build();
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RecommendedQuestionsResponse {
    private List<String> questions;
}
