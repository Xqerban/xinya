package com.xinya.dtx.core.network.dto

/**
 * PRO单个选项（single_choice 类型）
 */
data class ProOptionDto(
    val value: String,
    val label: String,
    val score: Int
)

/**
 * PRO问题DTO（与后端 ProQuestionDto 字段对齐，全部 camelCase）
 * type: "single_choice" | "scale"
 */
data class ProQuestionDto(
    val id: String,
    val title: String,
    val type: String = "single_choice",
    val options: List<ProOptionDto>? = null,
    val min: Int? = null,
    val max: Int? = null,
    val minLabel: String? = null,
    val maxLabel: String? = null
)

/**
 * PRO问题列表响应（checkedInToday 后端也是 camelCase）
 */
data class ProQuestionListDto(
    val checkedInToday: Boolean = false,
    val questions: List<ProQuestionDto> = emptyList()
)

/**
 * PRO提交请求（字段名必须与后端 ProSubmitRequest 完全一致，camelCase）
 */
data class ProSubmitRequest(
    val patientId: String,
    val recordDate: String,
    val answers: List<ProAnswer>
)

data class ProAnswer(
    val questionId: String,
    val answer: String,
    val score: Int
)

/**
 * PRO提交响应（与后端 ProSubmitResultDto 字段对齐，camelCase）
 */
data class ProSubmitResponse(
    val success: Boolean = false,
    val psychEnergyDelta: Int = 0,
    val hopeTreeExpDelta: Int = 0,
    val totalScore: Int = 0,
    val alertCreated: Boolean = false,
    val message: String? = null
)
