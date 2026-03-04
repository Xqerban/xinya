package com.xinya.dtx.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertItemDto {

    private String id;

    private String level;

    private String message;

    private String patientId;

    private String patientName;

    private String createdAt;

    private Boolean resolved;
}

