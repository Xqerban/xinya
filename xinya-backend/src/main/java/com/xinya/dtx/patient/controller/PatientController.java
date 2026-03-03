package com.xinya.dtx.patient.controller;

import com.xinya.dtx.common.response.ApiResponse;
import com.xinya.dtx.common.response.PageResult;
import com.xinya.dtx.patient.dto.CreatePatientRequest;
import com.xinya.dtx.patient.dto.EnergyTrendResponse;
import com.xinya.dtx.patient.dto.PatientDetailDto;
import com.xinya.dtx.patient.dto.PatientDto;
import com.xinya.dtx.patient.dto.UpdatePatientRequest;
import com.xinya.dtx.patient.service.PatientService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    public ApiResponse<PatientDto> createPatient(@Valid @RequestBody CreatePatientRequest request) {
        PatientDto dto = patientService.createPatient(request);
        return ApiResponse.success("创建成功", dto);
    }

    @GetMapping("/{id}")
    public ApiResponse<PatientDto> getPatient(@PathVariable("id") String id) {
        try {
            PatientDto dto = patientService.getPatientById(id);
            return ApiResponse.success(dto);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }

    @GetMapping
    public ApiResponse<PageResult<PatientDto>> listPatients(@RequestParam(value = "page", required = false) Integer page,
                                                            @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                            @RequestParam(value = "stage", required = false) String stage,
                                                            @RequestParam(value = "keyword", required = false) String keyword) {
        PageResult<PatientDto> result = patientService.listPatients(page, pageSize, stage, keyword);
        return ApiResponse.success(result);
    }

    @PutMapping("/{id}")
    public ApiResponse<PatientDto> updatePatient(@PathVariable("id") String id,
                                                 @RequestBody UpdatePatientRequest request) {
        try {
            PatientDto dto = patientService.updatePatient(id, request);
            return ApiResponse.success("更新成功", dto);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePatient(@PathVariable("id") String id) {
        try {
            patientService.deletePatient(id);
            return ApiResponse.success("患者档案已删除", null);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }

    @GetMapping("/{id}/energy-trend")
    public ApiResponse<EnergyTrendResponse> getEnergyTrend(@PathVariable("id") String id,
                                                           @RequestParam(value = "days", required = false) Integer days) {
        try {
            EnergyTrendResponse response = patientService.getEnergyTrend(id, days);
            return ApiResponse.success(response);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }

    @GetMapping("/{id}/detail")
    public ApiResponse<PatientDetailDto> getPatientDetail(@PathVariable("id") String id) {
        try {
            PatientDetailDto detail = patientService.getPatientDetail(id);
            return ApiResponse.success(detail);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }
}

