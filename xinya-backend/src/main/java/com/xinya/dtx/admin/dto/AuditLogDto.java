package com.xinya.dtx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {

    private Long id;

    private String operatorId;

    private String operatorName;

    private String action;

    private String targetType;

    private String targetId;

    private String detail;

    private String ipAddress;

    private String createdAt;
}

