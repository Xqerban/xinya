package com.xinya.dtx.clinical.service;

import com.xinya.dtx.clinical.dto.ClinicalStageHistoryDto;
import com.xinya.dtx.clinical.dto.ClinicalStageInfoDto;
import com.xinya.dtx.clinical.dto.StageTransitionRequest;
import com.xinya.dtx.patient.dto.PatientDto;

import java.util.List;

public interface ClinicalService {

    ClinicalStageInfoDto getCurrentStage(String patientId);

    PatientDto transition(StageTransitionRequest request);

    List<ClinicalStageHistoryDto> listHistory(String patientId);
}

