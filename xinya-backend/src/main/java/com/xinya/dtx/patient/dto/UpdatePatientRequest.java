package com.xinya.dtx.patient.dto;

import lombok.Data;

/**
 * 更新患者信息请求（所有字段可选，PUT 但语义类似 PATCH）
 */
@Data
public class UpdatePatientRequest {

    private String name;

    private String roomNumber;

    private String diagnosis;

    private Integer age;

    private String gender;
}

