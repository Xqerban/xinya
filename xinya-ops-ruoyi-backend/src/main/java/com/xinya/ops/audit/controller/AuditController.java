package com.xinya.ops.audit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.ops.audit.entity.OpsAuditLog;
import com.xinya.ops.audit.mapper.OpsAuditLogMapper;
import com.xinya.ops.common.domain.PageResult;
import com.xinya.ops.common.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final OpsAuditLogMapper auditLogMapper;

    @GetMapping
    public R<PageResult<OpsAuditLog>> listAuditLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {

        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize <= 0) ? 20 : pageSize;

        LocalDateTime start = StringUtils.hasText(startDate)
                ? LocalDate.parse(startDate, DATE_FMT).atStartOfDay() : null;
        LocalDateTime end = StringUtils.hasText(endDate)
                ? LocalDate.parse(endDate, DATE_FMT).plusDays(1).atStartOfDay().minusNanos(1) : null;

        var result = auditLogMapper.findByFilters(new Page<>(p, size),
                StringUtils.hasText(userId) ? userId : null,
                StringUtils.hasText(action) ? action : null,
                StringUtils.hasText(targetType) ? targetType : null,
                StringUtils.hasText(targetId) ? targetId : null,
                start, end);

        return R.ok(PageResult.<OpsAuditLog>builder()
                .list(result.getRecords()).total(result.getTotal()).page(p).pageSize(size).build());
    }
}
