package com.xinya.dtx.pro.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProHistoryAnswerDto {

    private String questionId;

    private String questionTitle;

    private String answer;

    private Integer score;
}

