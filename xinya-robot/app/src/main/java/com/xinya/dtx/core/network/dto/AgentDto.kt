package com.xinya.dtx.core.network.dto

/**
 * 智能体对话请求（字段名与后端 camelCase 对齐）
 */
data class AgentChatRequest(
    val patientId: String,
    val agentType: String,
    val message: String,
    val sessionId: String
)

/**
 * 智能体对话响应
 */
data class AgentChatResponse(
    val reply: String,
    val psychEnergyDelta: Int = 0,
    val recommendedQuestions: List<String> = emptyList(),
    val crisisAlert: Boolean = false
)

/**
 * 推荐问题响应
 */
data class RecommendedQuestionsResponse(
    val questions: List<String>
)
