package com.xinya.dtx.agent.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 后端调用 psych/nurse 智能体时的请求体 DTO，对应 Agent-api 文档中的 chat 请求结构。
 */
@Data
@Builder
public class AgentChatPayload {

    private String sessionId;

    private PatientContext patientContext;

    private List<HistoryItem> history;

    private String message;

    @Data
    @Builder
    public static class PatientContext {
        private String patientId;
        private String name;
        private String stage;
        private String stageName;
        private Integer daysInStage;
        private Integer psychEnergy;
        private Integer treeLevel;
        private Integer age;
        private String gender;
        private String diagnosis;
    }

    @Data
    @Builder
    public static class HistoryItem {
        private String role;    // user | assistant
        private String content;
    }
}

