package com.xinya.business.education.dto;

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
    private boolean completed;
    private String lastWatchedAt;
}
