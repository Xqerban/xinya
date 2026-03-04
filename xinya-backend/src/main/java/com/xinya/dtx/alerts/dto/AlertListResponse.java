package com.xinya.dtx.alerts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertListResponse {

    private List<AlertDto> list;

    private Long total;

    private Long unresolvedCount;
}

