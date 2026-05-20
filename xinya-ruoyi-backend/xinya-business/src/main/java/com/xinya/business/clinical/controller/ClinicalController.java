package com.xinya.business.clinical.controller;

import com.xinya.business.clinical.dto.*;
import com.xinya.business.clinical.service.ClinicalService;
import com.xinya.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "临床阶段")
@RestController
@RequestMapping("/api/clinical")
@RequiredArgsConstructor
public class ClinicalController {

    private final ClinicalService clinicalService;

    @Operation(summary = "获取患者阶段信息")
    @GetMapping("/stage/{patientId}")
    public R<PatientStageInfoDto> getStage(@PathVariable String patientId) {
        return R.ok(clinicalService.getPatientStageInfo(patientId));
    }

    @Operation(summary = "更新患者阶段")
    @PutMapping("/stage/{patientId}")
    public R<PatientStageInfoDto> updateStage(@PathVariable String patientId,
                                              @RequestBody UpdateStageRequest request) {
        return R.ok(clinicalService.updatePatientStage(patientId, request));
    }

    @Operation(summary = "获取阶段变更历史")
    @GetMapping("/stage/{patientId}/history")
    public R<List<ClinicalStageHistoryDto>> getHistory(@PathVariable String patientId) {
        return R.ok(clinicalService.getStageHistory(patientId));
    }
}
