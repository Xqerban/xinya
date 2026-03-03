package com.xinya.dtx.patient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDetailDto {

    private PatientDto patient;

    private HopeTreeDto hopeTree;

    private LatestProRecordDto latestProRecord;

    private Boolean todayCheckedIn;

    private Integer pendingAlerts;
}

