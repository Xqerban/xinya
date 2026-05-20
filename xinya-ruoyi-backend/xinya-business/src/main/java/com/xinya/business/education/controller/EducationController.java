package com.xinya.business.education.controller;

import com.xinya.business.education.dto.*;
import com.xinya.business.education.service.EducationService;
import com.xinya.common.core.domain.PageResult;
import com.xinya.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "健康教育")
@RestController
@RequestMapping("/api/education")
@RequiredArgsConstructor
public class EducationController {

    private final EducationService educationService;

    @Operation(summary = "获取教育内容列表")
    @GetMapping("/contents")
    public R<PageResult<EducationContentDto>> listContents(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String stage) {
        return R.ok(educationService.listContents(page, pageSize, category, stage));
    }

    @Operation(summary = "获取教育内容详情")
    @GetMapping("/contents/{id}")
    public R<EducationContentDto> getContent(@PathVariable String id) {
        return R.ok(educationService.getContentById(id));
    }

    @Operation(summary = "标记学习进度")
    @PostMapping("/progress/{patientId}/{contentId}")
    public R<EducationProgressDto> markProgress(@PathVariable String patientId,
                                               @PathVariable String contentId) {
        return R.ok(educationService.markProgress(patientId, contentId));
    }

    @Operation(summary = "获取患者学习进度")
    @GetMapping("/progress/{patientId}")
    public R<List<EducationProgressDto>> getProgress(@PathVariable String patientId) {
        return R.ok(educationService.getPatientProgress(patientId));
    }
}
