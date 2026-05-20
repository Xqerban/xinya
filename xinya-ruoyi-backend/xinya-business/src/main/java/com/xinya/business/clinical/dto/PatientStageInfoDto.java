package com.xinya.business.clinical.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PatientStageInfoDto {
    private String patientId;
    private String patientName;
    private String stage;
    private String stageStartDate;
    private String admissionDate;
}
