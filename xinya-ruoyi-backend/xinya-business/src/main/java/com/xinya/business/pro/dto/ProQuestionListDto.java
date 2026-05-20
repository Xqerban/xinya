package com.xinya.business.pro.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProQuestionListDto {
    private boolean checkedInToday;
    private List<ProQuestionDto> questions;
}
