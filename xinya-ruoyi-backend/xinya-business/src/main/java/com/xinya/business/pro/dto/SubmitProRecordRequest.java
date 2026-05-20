package com.xinya.business.pro.dto;

import lombok.Data;

import java.util.List;

@Data
public class SubmitProRecordRequest {
    private String patientId;
    private List<ProAnswerDto> answers;
}
