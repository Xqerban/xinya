package com.xinya.ops.education.controller;

import com.xinya.ops.common.response.ApiResponse;
import com.xinya.ops.common.response.PageResult;
import com.xinya.ops.education.dto.CreateEducationContentRequest;
import com.xinya.ops.education.dto.EducationContentDto;
import com.xinya.ops.education.dto.UpdateEducationContentRequest;
import com.xinya.ops.education.service.EducationService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/education/contents")
@RequiredArgsConstructor
public class EducationController {

    private final EducationService educationService;

    @GetMapping
    public ApiResponse<PageResult<EducationContentDto>> list(
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(
                educationService.listContents(stage, category, contentType, keyword, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<EducationContentDto> get(@PathVariable String id) {
        try {
            return ApiResponse.success(educationService.getContent(id));
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<EducationContentDto> create(
            @Valid @RequestBody CreateEducationContentRequest request,
            HttpServletRequest httpRequest) {
        String operatorId = (String) httpRequest.getAttribute("userId");
        return ApiResponse.success(educationService.createContent(request, operatorId));
    }

    @PutMapping("/{id}")
    public ApiResponse<EducationContentDto> update(
            @PathVariable String id,
            @RequestBody UpdateEducationContentRequest request) {
        try {
            return ApiResponse.success(educationService.updateContent(id, request));
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deactivate(@PathVariable String id) {
        try {
            educationService.deactivateContent(id);
            return ApiResponse.success("已下架", null);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }
}
