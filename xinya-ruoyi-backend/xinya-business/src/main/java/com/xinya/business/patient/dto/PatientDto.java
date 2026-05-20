package com.xinya.business.patient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDto {
    private String id;
    private String name;
    private String stage;
    private Integer psychEnergy;
    private Integer treeLevel;
    private String admissionDate;
    private String roomNumber;
    private String createdAt;
    private String updatedAt;
}
