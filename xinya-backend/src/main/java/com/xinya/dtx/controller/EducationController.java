package com.xinya.dtx.controller;

import com.xinya.dtx.common.dto.ApiResponse;
import com.xinya.dtx.dto.EducationDto;
import com.xinya.dtx.service.EducationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/education")
@RequiredArgsConstructor
@Tag(name = "护理宣教", description = "宣教内容管理")
public class EducationController {
    
    private final EducationService educationService;
    
    @GetMapping("/contents")
    @Operation(summary = "获取宣教内容列表")
    public ApiResponse<Map<String, Object>> getContents(
        @RequestParam(required = false) String category,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        List<EducationDto> contents = educationService.getContents(category, page, pageSize);
        int total = educationService.getTotalCount(category);
        
        // 如果没有数据，total使用mock数据的数量
        if (total == 0) {
            total = contents.size();
        }
        
        return ApiResponse.success(Map.of(
            "contents", contents,
            "total", total
        ));
    }
}
