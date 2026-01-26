package com.xinya.dtx.core.network.dto

import com.google.gson.annotations.SerializedName

/**
 * PRO提交请求
 */
data class ProSubmitRequest(
    @SerializedName("patient_id")
    val patientId: String,
    @SerializedName("record_date")
    val recordDate: String,
    val answers: List<ProAnswer>
)

data class ProAnswer(
    @SerializedName("question_id")
    val questionId: String,
    val answer: String,
    val score: Int
)

/**
 * PRO提交响应
 */
data class ProSubmitResponse(
    val success: Boolean,
    @SerializedName("psych_energy_delta")
    val psychEnergyDelta: Int = 0,
    val message: String? = null
)
