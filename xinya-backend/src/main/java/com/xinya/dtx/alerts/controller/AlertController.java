package com.xinya.dtx.alerts.controller;

import com.xinya.dtx.alerts.dto.AlertDto;
import com.xinya.dtx.alerts.dto.AlertListResponse;
import com.xinya.dtx.alerts.dto.CreateAlertRequest;
import com.xinya.dtx.alerts.dto.ResolveAlertRequest;
import com.xinya.dtx.alerts.service.AlertService;
import com.xinya.dtx.common.response.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ApiResponse<AlertListResponse> list(@RequestParam(value = "resolved", required = false) Boolean resolved,
                                               @RequestParam(value = "level", required = false) String level,
                                               @RequestParam(value = "patientId", required = false) String patientId,
                                               @RequestParam(value = "page", required = false) Integer page,
                                               @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        AlertListResponse resp = alertService.list(resolved, level, patientId, page, pageSize);
        return ApiResponse.success(resp);
    }

    @PutMapping("/{id}/resolve")
    public ApiResponse<AlertDto> resolve(@PathVariable String id,
                                         @RequestBody ResolveAlertRequest request) {
        try {
            AlertDto dto = alertService.resolve(id, request);
            return ApiResponse.success(dto);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<AlertDto> create(@Valid @RequestBody CreateAlertRequest request) {
        try {
            AlertDto dto = alertService.create(request);
            return ApiResponse.success(dto);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }
}

