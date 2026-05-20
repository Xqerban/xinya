package com.xinya.business.patient.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreatePatientRequest {
    @NotBlank
    private String name;
    private Integer age;
    private String gender;
    private String diagnosis;
    @NotBlank
    private String admissionDate;
    private String roomNumber;
}
