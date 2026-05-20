package com.xinya.business.agent.dto;

import lombok.Data;

@Data
public class NurseChatRequest {
    private String userId;
    private String patientId;
    private String message;
    private String conversationId;
}
