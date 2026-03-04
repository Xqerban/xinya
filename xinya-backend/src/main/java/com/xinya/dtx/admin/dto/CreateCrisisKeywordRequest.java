package com.xinya.dtx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCrisisKeywordRequest {

    @NotBlank
    private String keyword;

    @NotBlank
    private String crisisLevel; // warning | critical
}

