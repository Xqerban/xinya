package com.xinya.business.alerts.controller;

import com.xinya.business.alerts.dto.*;
import com.xinya.business.alerts.service.AlertService;
import com.xinya.common.core.domain.PageResult;
import com.xinya.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "告警管理")
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @Operation(summary = "分页查询告警")
    @GetMapping
    public R<PageResult<AlertDto>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) Boolean resolved) {
        return R.ok(alertService.listAlerts(page, pageSize, patientId, resolved));
    }

    @Operation(summary = "获取告警详情")
    @GetMapping("/{id}")
    public R<AlertDto> getById(@PathVariable String id) {
        return R.ok(alertService.getAlertById(id));
    }

    @Operation(summary = "处理告警")
    @PutMapping("/{id}/resolve")
    public R<AlertDto> resolve(@PathVariable String id,
                               @RequestBody ResolveAlertRequest request) {
        return R.ok(alertService.resolveAlert(id, request));
    }

    @Operation(summary = "创建告警")
    @PostMapping
    public R<AlertDto> create(@RequestBody CreateAlertRequest request) {
        return R.ok(alertService.createAlert(request));
    }
}
