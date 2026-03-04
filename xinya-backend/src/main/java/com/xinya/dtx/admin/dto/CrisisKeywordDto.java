package com.xinya.dtx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrisisKeywordDto {

    private Long id;

    private String keyword;

    private String crisisLevel;

    private Boolean isActive;

    private String createdBy;

    private String createdAt;
}

