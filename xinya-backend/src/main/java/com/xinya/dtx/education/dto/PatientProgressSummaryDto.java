package com.xinya.dtx.education.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientProgressSummaryDto {

    private Integer totalContents;
    private Integer completedContents;
    private Double completionRate;
    private Integer totalWatchedSeconds;
    private List<PatientProgressItemDto> progressList;
}
