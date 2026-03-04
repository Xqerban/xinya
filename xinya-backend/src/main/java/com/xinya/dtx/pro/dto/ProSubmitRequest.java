package com.xinya.dtx.pro.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ProSubmitRequest {

    @NotBlank
    private String patientId;

    /**
     * yyyy-MM-dd，不传则默认今天
     */
    private String recordDate;

    private List<ProAnswerDto> answers;

    private Long clientTimestamp;
}

