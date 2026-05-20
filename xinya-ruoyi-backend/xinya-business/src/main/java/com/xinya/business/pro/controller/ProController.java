package com.xinya.business.pro.controller;

import com.xinya.business.pro.dto.*;
import com.xinya.business.pro.service.ProService;
import com.xinya.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "PRO 量表")
@RestController
@RequestMapping("/api/pro")
@RequiredArgsConstructor
public class ProController {

    private final ProService proService;

    @Operation(summary = "获取量表题目")
    @GetMapping("/questions")
    public R<List<ProQuestionDto>> getQuestions(@RequestParam(required = false) String stage) {
        return R.ok(proService.getQuestions(stage));
    }

    @Operation(summary = "提交量表")
    @PostMapping("/records")
    public R<ProRecordResultDto> submit(@Valid @RequestBody SubmitProRecordRequest request) {
        return R.ok(proService.submitProRecord(request));
    }

    @Operation(summary = "获取某日量表结果")
    @GetMapping("/records/{patientId}")
    public R<ProRecordResultDto> getResult(@PathVariable String patientId,
                                          @RequestParam(required = false) String recordDate) {
        return R.ok(proService.getProResult(patientId, recordDate));
    }
}
