package com.xinya.business.hopetree.controller;

import com.xinya.business.hopetree.dto.*;
import com.xinya.business.hopetree.service.HopeTreeService;
import com.xinya.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "希望树")
@RestController
@RequestMapping("/api/hopetree")
@RequiredArgsConstructor
public class HopeTreeController {

    private final HopeTreeService hopeTreeService;

    @Operation(summary = "获取患者希望树信息")
    @GetMapping("/{patientId}")
    public R<HopeTreeDetailDto> getTree(@PathVariable String patientId) {
        return R.ok(hopeTreeService.getPatientTree(patientId));
    }

    @Operation(summary = "每日打卡")
    @PostMapping("/{patientId}/check-in")
    public R<HopeTreeDetailDto> checkIn(@PathVariable String patientId) {
        return R.ok(hopeTreeService.dailyCheckIn(patientId));
    }

    @Operation(summary = "获取成长历史")
    @GetMapping("/{patientId}/history")
    public R<List<HopeTreeGrowthHistoryDto>> getHistory(@PathVariable String patientId,
                                                        @RequestParam(defaultValue = "30") Integer limit) {
        return R.ok(hopeTreeService.getGrowthHistory(patientId, limit));
    }
}
