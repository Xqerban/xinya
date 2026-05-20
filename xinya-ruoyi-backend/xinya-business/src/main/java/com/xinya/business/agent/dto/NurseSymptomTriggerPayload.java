package com.xinya.business.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NurseSymptomTriggerPayload {
    private String patientId;
    private PatientContext patientContext;
    private String triggerSource;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatientContext {
        private String stage;
        private Integer psychEnergy;
    }
}
