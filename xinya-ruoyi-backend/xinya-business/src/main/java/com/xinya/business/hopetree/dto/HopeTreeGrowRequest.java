package com.xinya.business.hopetree.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HopeTreeGrowRequest {
    @NotBlank
    private String patientId;
    private String growthSource;
    private int expAmount;
}
