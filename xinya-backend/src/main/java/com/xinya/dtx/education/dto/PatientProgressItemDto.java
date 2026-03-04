package com.xinya.dtx.education.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientProgressItemDto {

    private String contentId;
    private String contentTitle;
    private Integer watchedSeconds;
    private Boolean completed;
    private String lastWatchedAt;
}
