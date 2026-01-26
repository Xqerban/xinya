package com.xinya.dtx.core.network.dto

import com.google.gson.annotations.SerializedName

/**
 * 智能体对话请求
 */
data class AgentChatRequest(
    @SerializedName("patient_id")
    val patientId: String,
    @SerializedName("agent_type")
    val agentType: String,              // "psych" | "nurse"
    val message: String,
    @SerializedName("session_id")
    val sessionId: String
)

/**
 * 智能体对话响应
 */
data class AgentChatResponse(
    val reply: String,
    @SerializedName("psych_energy_delta")
    val psychEnergyDelta: Int = 0,
    @SerializedName("recommended_questions")
    val recommendedQuestions: List<String> = emptyList(),
    @SerializedName("crisis_alert")
    val crisisAlert: Boolean = false
)

/**
 * 推荐问题响应
 */
data class RecommendedQuestionsResponse(
    val questions: List<String>
)
