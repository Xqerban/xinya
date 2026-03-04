package com.xinya.dtx.pro.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProQuestionDto {

    private String id;

    private String title;

    private String type;

    /**
     * 选项结构（single_choice / multi_choice）。
     * 使用 Jackson 的 JsonNode，便于直接序列化为 JSON 数组/对象。
     */
    private JsonNode options;

    private Integer min;

    private Integer max;

    private String minLabel;

    private String maxLabel;
}

