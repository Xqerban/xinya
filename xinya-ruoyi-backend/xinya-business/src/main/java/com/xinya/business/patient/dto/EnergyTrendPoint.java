package com.xinya.business.patient.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EnergyTrendPoint {
    private String date;
    private Integer psychEnergy;
}
