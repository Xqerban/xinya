package com.xinya.dtx.education.controller;

import com.xinya.dtx.common.response.ApiResponse;
import com.xinya.dtx.common.response.PageResult;
import com.xinya.dtx.education.dto.EducationContentDto;
import com.xinya.dtx.education.dto.EducationProgressRequest;
import com.xinya.dtx.education.dto.EducationProgressResponse;
import com.xinya.dtx.education.dto.PatientProgressSummaryDto;
import com.xinya.dtx.education.service.EducationService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/education")
@RequiredArgsConstructor
public class EducationController {

    private final EducationService educationService;

    @GetMapping("/contents")
    public ApiResponse<PageResult<EducationContentDto>> getContents(
            @RequestParam(value = "stage", required = false) String stage,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        PageResult<EducationContentDto> result = educationService.getContents(
                stage, category, contentType, keyword, page, pageSize);
        return ApiResponse.success(result);
    }

    @GetMapping("/contents/{id}")
    public ApiResponse<EducationContentDto> getContentDetail(@PathVariable String id) {
        try {
            EducationContentDto dto = educationService.getContentDetail(id);
            return ApiResponse.success(dto);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PostMapping("/progress")
    public ApiResponse<EducationProgressResponse> recordProgress(
            @Valid @RequestBody EducationProgressRequest request) {
        try {
            EducationProgressResponse response = educationService.recordProgress(request);
            return ApiResponse.success(response);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @GetMapping("/progress/{patientId}")
    public ApiResponse<PatientProgressSummaryDto> getPatientProgress(@PathVariable String patientId) {
        try {
            PatientProgressSummaryDto dto = educationService.getPatientProgress(patientId);
            return ApiResponse.success(dto);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

}
