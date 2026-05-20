package com.xinya.business.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatPayload {
    private String sessionId;
    private PatientContext patientContext;
    private List<HistoryItem> history;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryItem {
        private String role;
        private String content;
    }
}
