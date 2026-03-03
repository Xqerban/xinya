package com.xinya.dtx.patient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 患者基础信息 DTO（与 API 文档 PatientDto 对齐）
 */
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

