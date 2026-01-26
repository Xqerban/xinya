package com.xinya.dtx.controller;

import com.xinya.dtx.dto.ApiResponse;
import com.xinya.dtx.dto.PatientDto;
import com.xinya.dtx.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Tag(name = "患者管理", description = "患者档案的增删改查接口")
public class PatientController {
    
    private final PatientService patientService;
    
    @PostMapping
    @Operation(summary = "创建患者档案")
    public ApiResponse<PatientDto> createPatient(@RequestBody CreatePatientRequest request) {
        PatientDto patient = patientService.createPatient(
            request.getName(),
            request.getRoomNumber(),
            request.getAdmissionDate()
        );
        return ApiResponse.success("患者创建成功", patient);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "获取患者信息")
    public ApiResponse<PatientDto> getPatient(@PathVariable String id) {
        return patientService.getPatient(id)
            .map(ApiResponse::success)
            .orElse(ApiResponse.error(404, "患者不存在"));
    }
    
    @GetMapping
    @Operation(summary = "获取所有患者列表")
    public ApiResponse<List<PatientDto>> getAllPatients() {
        return ApiResponse.success(patientService.getAllPatients());
    }
    
    @Data
    static class CreatePatientRequest {
        private String name;
        private String roomNumber;
        private LocalDate admissionDate;
    }
}
