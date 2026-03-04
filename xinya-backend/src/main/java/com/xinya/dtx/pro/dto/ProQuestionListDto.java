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
public class ProQuestionListDto {

    private Boolean checkedInToday;

    private List<ProQuestionDto> questions;
}

