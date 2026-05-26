package com.xinya.ops.config.dto;

import lombok.*;

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
