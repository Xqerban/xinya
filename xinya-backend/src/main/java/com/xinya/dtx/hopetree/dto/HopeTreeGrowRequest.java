package com.xinya.dtx.hopetree.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HopeTreeGrowRequest {

    @NotBlank
    private String patientId;

    @NotBlank
    private String growthSource; // check_in | education | conversation | stage_advance | meditation

    @NotNull
    @Min(1)
    private Integer expAmount;
}
