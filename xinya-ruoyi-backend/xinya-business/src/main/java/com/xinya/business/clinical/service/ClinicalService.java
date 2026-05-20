package com.xinya.business.clinical.service;

import com.xinya.business.clinical.dto.*;

import java.util.List;

public interface ClinicalService {
    PatientStageInfoDto getPatientStageInfo(String patientId);
    PatientStageInfoDto updatePatientStage(String patientId, UpdateStageRequest request);
    List<ClinicalStageHistoryDto> getStageHistory(String patientId);
}
