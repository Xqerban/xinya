package com.xinya.dtx.education.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationProgressResponse {

    private Integer hopeTreeExpDelta;
    private Double completionRate;
}
