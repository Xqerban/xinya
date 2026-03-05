package com.xinya.dtx.core.network.dto

/**
 * 希望之树状态响应
 */
data class HopeTreeStatusResponse(
    val currentLevel: Int,
    val currentExp: Int,
    val nextLevelExp: Int,
    val totalGrowthDays: Int
)

/**
 * 希望之树生长请求
 */
data class HopeTreeGrowRequest(
    val patientId: String,
    val growthSource: String,
    val expAmount: Int
)

/**
 * 希望之树生长响应
 */
data class HopeTreeGrowResponse(
    val success: Boolean,
    val newLevel: Int,
    val newExp: Int,
    val levelUp: Boolean = false
)
