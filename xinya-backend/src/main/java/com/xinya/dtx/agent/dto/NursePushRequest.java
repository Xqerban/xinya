package com.xinya.dtx.agent.dto;

import lombok.Data;

@Data
public class NursePushRequest {

    private String patientId;

    private String triggerType; // symptom | stage | scheduled

    private String symptomKeyword;

    private String currentStage;
}

