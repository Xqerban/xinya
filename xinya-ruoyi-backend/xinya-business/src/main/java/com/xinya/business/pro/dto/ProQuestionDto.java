package com.xinya.business.pro.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProQuestionDto {
    private String id;
    private String stage;
    private String title;
    private String type;
    private String options;
    private Integer scaleMin;
    private Integer scaleMax;
    private String minLabel;
    private String maxLabel;
    private String symptomKey;
    private Integer sortOrder;
}
