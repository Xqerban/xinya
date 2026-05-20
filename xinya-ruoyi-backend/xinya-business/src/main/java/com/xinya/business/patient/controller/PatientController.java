package com.xinya.business.patient.controller;

import com.xinya.business.patient.dto.*;
import com.xinya.business.patient.service.PatientService;
import com.xinya.common.core.domain.PageResult;
import com.xinya.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "患者管理")
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @Operation(summary = "创建患者")
    @PostMapping
    public R<PatientDto> create(@Valid @RequestBody CreatePatientRequest request) {
        return R.ok(patientService.createPatient(request));
    }

    @Operation(summary = "获取患者信息")
    @GetMapping("/{id}")
    public R<PatientDto> getById(@PathVariable String id) {
        return R.ok(patientService.getPatientById(id));
    }

    @Operation(summary = "患者详情（含树/PRO/告警统计）")
    @GetMapping("/{id}/detail")
    public R<PatientDetailDto> getDetail(@PathVariable String id) {
        return R.ok(patientService.getPatientDetail(id));
    }

    @Operation(summary = "分页查询患者列表")
    @GetMapping
    public R<PageResult<PatientDto>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String keyword) {
        return R.ok(patientService.listPatients(page, pageSize, stage, keyword));
    }

    @Operation(summary = "更新患者信息")
    @PutMapping("/{id}")
    public R<PatientDto> update(@PathVariable String id,
                                @RequestBody UpdatePatientRequest request) {
        return R.ok(patientService.updatePatient(id, request));
    }

    @Operation(summary = "删除患者")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        patientService.deletePatient(id);
        return R.ok();
    }

    @Operation(summary = "心理能量趋势")
    @GetMapping("/{id}/energy-trend")
    public R<EnergyTrendResponse> energyTrend(@PathVariable String id,
                                              @RequestParam(defaultValue = "7") Integer days) {
        return R.ok(patientService.getEnergyTrend(id, days));
    }
}
