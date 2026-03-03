package com.xinya.dtx.clinical.controller;

import com.xinya.dtx.clinical.dto.ClinicalStageHistoryDto;
import com.xinya.dtx.clinical.dto.ClinicalStageInfoDto;
import com.xinya.dtx.clinical.dto.StageTransitionRequest;
import com.xinya.dtx.clinical.service.ClinicalService;
import com.xinya.dtx.common.response.ApiResponse;
import com.xinya.dtx.patient.dto.PatientDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clinical")
@RequiredArgsConstructor
public class ClinicalController {

    private final ClinicalService clinicalService;

    @GetMapping("/stage/{patientId}")
    public ApiResponse<ClinicalStageInfoDto> getCurrentStage(@PathVariable("patientId") String patientId) {
        try {
            ClinicalStageInfoDto info = clinicalService.getCurrentStage(patientId);
            return ApiResponse.success(info);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }

    @PostMapping("/transition")
    public ApiResponse<PatientDto> transition(@Valid @RequestBody StageTransitionRequest request) {
        try {
            PatientDto dto = clinicalService.transition(request);
            return ApiResponse.success(dto);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        } catch (IllegalStateException e) {
            return ApiResponse.error(409, e.getMessage());
        }
    }

    @GetMapping("/history/{patientId}")
    public ApiResponse<List<ClinicalStageHistoryDto>> getHistory(@PathVariable("patientId") String patientId) {
        try {
            List<ClinicalStageHistoryDto> list = clinicalService.listHistory(patientId);
            return ApiResponse.success(list);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }
}

