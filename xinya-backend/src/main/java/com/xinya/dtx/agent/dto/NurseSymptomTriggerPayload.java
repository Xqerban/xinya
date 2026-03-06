package com.xinya.dtx.agent.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 后端调用小护士情景触发接口 /v1/nurse/symptom-trigger 的请求体 DTO。
 * 当前实现仅使用文档中的部分字段，后续可按需扩展。
 */
@Data
@Builder
public class NurseSymptomTriggerPayload {

    private String patientId;

    private PatientContext patientContext;

    private String triggerSource;

    private List<DetectedSymptom> detectedSymptoms;

    private List<String> viewedContentIds;

    @Data
    @Builder
    public static class PatientContext {
        private String stage;
        private String stageName;
        private Integer daysInStage;
        private Integer psychEnergy;
    }

    @Data
    @Builder
    public static class DetectedSymptom {
        private String symptomKey;
        private String symptomName;
        private Integer score;
        private Integer maxScore;
    }
}

