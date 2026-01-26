package com.xinya.dtx.dto;

import com.xinya.dtx.entity.ClinicalStage;
import com.xinya.dtx.entity.Patient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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
    private LocalDate admissionDate;
    private String roomNumber;
    
    public static PatientDto fromEntity(Patient patient) {
        return PatientDto.builder()
            .id(patient.getId())
            .name(patient.getName())
            .stage(patient.getStage().name())
            .psychEnergy(patient.getPsychEnergy())
            .treeLevel(patient.getTreeLevel())
            .admissionDate(patient.getAdmissionDate())
            .roomNumber(patient.getRoomNumber())
            .build();
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class CreatePatientRequest {
    private String name;
    private String roomNumber;
    private LocalDate admissionDate;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class UpdateStageRequest {
    private String patientId;
    private String targetStage;
}
