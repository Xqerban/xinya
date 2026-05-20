package com.xinya.business.pro.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProRecordResultDto {
    private String patientId;
    private String recordDate;
    private Integer totalScore;
    private Integer psychEnergyDelta;
    private Integer newPsychEnergy;
    private List<ProAnswerDto> answers;
}
