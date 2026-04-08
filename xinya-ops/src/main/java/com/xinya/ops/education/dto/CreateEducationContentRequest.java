package com.xinya.ops.education.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateEducationContentRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String stage;

    @NotBlank(message = "分类不能为空")
    private String category;

    private String description;

    @NotBlank(message = "内容类型不能为空")
    private String contentType;

    private Integer durationSeconds;
    private String thumbnailUrl;
    private String mediaUrl;
    private List<String> tags;
    private Integer sortOrder;
}
