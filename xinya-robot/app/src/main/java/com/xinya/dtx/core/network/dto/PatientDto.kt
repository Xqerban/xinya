package com.xinya.dtx.core.network.dto

/**
 * 患者信息DTO
 */
data class PatientDto(
    val id: String,
    val name: String,
    val stage: String,
    val psychEnergy: Int,
    val treeLevel: Int,
    val admissionDate: String,
    val roomNumber: String?
)

/**
 * 创建患者请求
 */
data class CreatePatientRequest(
    val name: String,
    val roomNumber: String?,
    val admissionDate: String
)

/**
 * 更新患者阶段请求
 */
data class UpdateStageRequest(
    val patientId: String,
    val targetStage: String
)
