package com.xinya.dtx.core.network.dto

import com.google.gson.annotations.SerializedName

/**
 * 患者信息DTO
 */
data class PatientDto(
    val id: String,
    val name: String,
    val stage: String,
    @SerializedName("psych_energy")
    val psychEnergy: Int,
    @SerializedName("tree_level")
    val treeLevel: Int,
    @SerializedName("admission_date")
    val admissionDate: String,
    @SerializedName("room_number")
    val roomNumber: String?
)

/**
 * 创建患者请求
 */
data class CreatePatientRequest(
    val name: String,
    @SerializedName("room_number")
    val roomNumber: String?,
    @SerializedName("admission_date")
    val admissionDate: String
)

/**
 * 更新患者阶段请求
 */
data class UpdateStageRequest(
    @SerializedName("patient_id")
    val patientId: String,
    @SerializedName("target_stage")
    val targetStage: String
)
