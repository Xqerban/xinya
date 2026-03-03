package com.xinya.dtx.patient.service;

import com.xinya.dtx.common.response.PageResult;
import com.xinya.dtx.patient.dto.CreatePatientRequest;
import com.xinya.dtx.patient.dto.EnergyTrendResponse;
import com.xinya.dtx.patient.dto.PatientDetailDto;
import com.xinya.dtx.patient.dto.PatientDto;
import com.xinya.dtx.patient.dto.UpdatePatientRequest;

public interface PatientService {

    PatientDto createPatient(CreatePatientRequest request);

    PatientDto getPatientById(String id);

    PageResult<PatientDto> listPatients(Integer page, Integer pageSize, String stage, String keyword);

    PatientDto updatePatient(String id, UpdatePatientRequest request);

    void deletePatient(String id);

    EnergyTrendResponse getEnergyTrend(String patientId, Integer days);

    PatientDetailDto getPatientDetail(String patientId);
}

