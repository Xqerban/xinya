package com.xinya.dtx.pro.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProHistoryPageDto {

    private List<ProHistoryItemDto> list;

    private Long total;

    private Integer continuousCheckInDays;
}

