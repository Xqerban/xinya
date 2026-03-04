package com.xinya.dtx.alerts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDto {

    private String id;

    private String patientId;

    private String patientName;

    private String alertType;

    private String level;

    private String message;

    private String triggerMessage;

    private Boolean resolved;

    private String resolvedBy;

    private String resolvedNote;

    private String resolvedAt;

    private String createdAt;
}

