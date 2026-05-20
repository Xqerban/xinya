package com.xinya.business.patient.dto;

import lombok.Data;

@Data
public class UpdatePatientRequest {
    private String name;
    private Integer age;
    private String gender;
    private String diagnosis;
    private String roomNumber;
}
