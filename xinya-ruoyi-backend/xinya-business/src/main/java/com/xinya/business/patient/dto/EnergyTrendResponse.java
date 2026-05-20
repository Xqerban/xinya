package com.xinya.business.patient.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EnergyTrendResponse {
    private String patientId;
    private List<EnergyTrendPoint> trend;
    private Double avgEnergy;
    private Integer minEnergy;
    private Integer maxEnergy;
}
