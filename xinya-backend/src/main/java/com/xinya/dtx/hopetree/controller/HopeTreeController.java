package com.xinya.dtx.hopetree.controller;

import com.xinya.dtx.common.response.ApiResponse;
import com.xinya.dtx.common.response.PageResult;
import com.xinya.dtx.hopetree.dto.GrowthHistoryItemDto;
import com.xinya.dtx.hopetree.dto.HopeTreeGrowRequest;
import com.xinya.dtx.hopetree.dto.HopeTreeGrowResponse;
import com.xinya.dtx.hopetree.service.HopeTreeService;
import com.xinya.dtx.patient.dto.HopeTreeDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hopetree")
@RequiredArgsConstructor
public class HopeTreeController {

    private final HopeTreeService hopeTreeService;

    @GetMapping("/{patientId}")
    public ApiResponse<HopeTreeDto> getState(@PathVariable String patientId) {
        try {
            HopeTreeDto dto = hopeTreeService.getState(patientId);
            return ApiResponse.success(dto);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }

    @PostMapping("/grow")
    public ApiResponse<HopeTreeGrowResponse> grow(@Valid @RequestBody HopeTreeGrowRequest request) {
        try {
            HopeTreeGrowResponse response = hopeTreeService.grow(request);
            return ApiResponse.success(response);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }

    @GetMapping("/{patientId}/history")
    public ApiResponse<PageResult<GrowthHistoryItemDto>> getHistory(
            @PathVariable String patientId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        try {
            PageResult<GrowthHistoryItemDto> result = hopeTreeService.getHistory(patientId, page, pageSize);
            return ApiResponse.success(result);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }
}
