package com.xinya.ops.audit.controller;

import com.xinya.ops.audit.entity.OpsAuditLog;
import com.xinya.ops.audit.mapper.OpsAuditLogMapper;
import com.xinya.ops.common.response.ApiResponse;
import com.xinya.ops.common.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final OpsAuditLogMapper auditLogMapper;

    @GetMapping
    public ApiResponse<PageResult<OpsAuditLog>> listAuditLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {

        int p = (page == null || page < 1) ? 0 : page - 1;
        int size = (pageSize == null || pageSize <= 0) ? 20 : pageSize;

        LocalDateTime start = StringUtils.hasText(startDate)
                ? LocalDate.parse(startDate, DATE_FMT).atStartOfDay() : null;
        LocalDateTime end = StringUtils.hasText(endDate)
                ? LocalDate.parse(endDate, DATE_FMT).plusDays(1).atStartOfDay().minusNanos(1) : null;

        Page<OpsAuditLog> result = auditLogMapper.findByFilters(
                StringUtils.hasText(userId) ? userId : null,
                StringUtils.hasText(action) ? action : null,
                StringUtils.hasText(targetType) ? targetType : null,
                StringUtils.hasText(targetId) ? targetId : null,
                start, end, PageRequest.of(p, size));

        return ApiResponse.success(PageResult.<OpsAuditLog>builder()
                .list(result.getContent())
                .total(result.getTotalElements())
                .page(p + 1)
                .pageSize(size)
                .build());
    }
}
