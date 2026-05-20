package com.xinya.business.pro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProAnswerDto {
    private String questionId;
    private String answer;
}
