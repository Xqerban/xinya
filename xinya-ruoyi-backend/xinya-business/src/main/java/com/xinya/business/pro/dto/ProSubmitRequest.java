package com.xinya.business.pro.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ProSubmitRequest {
    @NotBlank
    private String patientId;
    private String recordDate;
    private List<ProAnswerDto> answers;
    private Long clientTimestamp;
}
