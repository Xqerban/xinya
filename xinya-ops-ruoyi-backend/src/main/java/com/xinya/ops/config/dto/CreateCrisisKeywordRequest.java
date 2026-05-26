package com.xinya.ops.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCrisisKeywordRequest {
    @NotBlank(message = "关键词不能为空")
    private String keyword;
    @NotBlank(message = "危机等级不能为空")
    private String crisisLevel;
}
