package com.xinya.ops.education.controller;

import com.xinya.ops.common.domain.PageResult;
import com.xinya.ops.common.domain.R;
import com.xinya.ops.education.dto.CreateEducationContentRequest;
import com.xinya.ops.education.dto.EducationContentDto;
import com.xinya.ops.education.dto.UpdateEducationContentRequest;
import com.xinya.ops.education.service.EducationService;
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
    public R<PageResult<EducationContentDto>> list(
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return R.ok(educationService.listContents(stage, category, contentType, keyword, page, pageSize));
    }

    @GetMapping("/{id}")
    public R<EducationContentDto> get(@PathVariable String id) {
        try {
            return R.ok(educationService.getContent(id));
        } catch (IllegalArgumentException e) {
            return R.fail(404, e.getMessage());
        }
    }

    @PostMapping
    public R<EducationContentDto> create(
            @Valid @RequestBody CreateEducationContentRequest request,
            HttpServletRequest httpRequest) {
        String operatorId = (String) httpRequest.getAttribute("userId");
        return R.ok(educationService.createContent(request, operatorId));
    }

    @PutMapping("/{id}")
    public R<EducationContentDto> update(@PathVariable String id,
                                          @RequestBody UpdateEducationContentRequest request) {
        try {
            return R.ok(educationService.updateContent(id, request));
        } catch (IllegalArgumentException e) {
            return R.fail(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public R<Void> deactivate(@PathVariable String id) {
        try {
            educationService.deactivateContent(id);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(404, e.getMessage());
        }
    }
}
