package com.xinya.dtx.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建患者档案请求
 */
@Data
public class CreatePatientRequest {

    @NotBlank
    private String name;

    private String roomNumber;

    @NotNull
    private String admissionDate; // yyyy-MM-dd

    private String diagnosis;

    private Integer age;

    private String gender; // MALE / FEMALE
}

