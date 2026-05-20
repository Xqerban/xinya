package com.xinya.business.education.dto;

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
    private int totalContents;
    private int completedContents;
    private double completionRate;
    private int totalWatchedSeconds;
    private List<PatientProgressItemDto> progressList;
}
