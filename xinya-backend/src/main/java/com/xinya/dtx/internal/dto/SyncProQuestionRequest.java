package com.xinya.dtx.internal.dto;

import lombok.Data;

@Data
public class SyncProQuestionRequest {
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
    private Boolean isActive;
}
