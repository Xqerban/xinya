package com.xinya.business.patient.service;

import com.xinya.business.patient.dto.*;
import com.xinya.common.core.domain.PageResult;

public interface PatientService {
    PatientDto createPatient(CreatePatientRequest request);
    PatientDto getPatientById(String id);
    PageResult<PatientDto> listPatients(Integer page, Integer pageSize, String stage, String keyword);
    PatientDto updatePatient(String id, UpdatePatientRequest request);
    void deletePatient(String id);
    EnergyTrendResponse getEnergyTrend(String patientId, Integer days);
    PatientDetailDto getPatientDetail(String patientId);
}
