package com.xinya.business.education.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationProgressResponse {
    private int hopeTreeExpDelta;
    private double completionRate;
}
