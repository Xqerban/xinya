package com.xinya.dtx.core.network.dto

import com.google.gson.annotations.SerializedName

/**
 * 希望之树状态响应
 */
data class HopeTreeStatusResponse(
    @SerializedName("current_level")
    val currentLevel: Int,
    @SerializedName("current_exp")
    val currentExp: Int,
    @SerializedName("next_level_exp")
    val nextLevelExp: Int,
    @SerializedName("total_growth_days")
    val totalGrowthDays: Int
)

/**
 * 希望之树生长请求
 */
data class HopeTreeGrowRequest(
    @SerializedName("patient_id")
    val patientId: String,
    @SerializedName("growth_source")
    val growthSource: String,           // "check_in" | "education" | "conversation"
    @SerializedName("exp_amount")
    val expAmount: Int
)

/**
 * 希望之树生长响应
 */
data class HopeTreeGrowResponse(
    val success: Boolean,
    @SerializedName("new_level")
    val newLevel: Int,
    @SerializedName("new_exp")
    val newExp: Int,
    @SerializedName("level_up")
    val levelUp: Boolean = false
)
