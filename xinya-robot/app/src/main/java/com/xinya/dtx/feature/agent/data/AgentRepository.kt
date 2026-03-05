package com.xinya.dtx.feature.agent.data

import com.xinya.dtx.core.network.ApiService
import com.xinya.dtx.core.network.dto.AgentChatRequest
import com.xinya.dtx.core.network.dto.AgentChatResponse
import com.xinya.dtx.core.network.dto.RecommendedQuestionsResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun chat(
        patientId: String,
        agentType: String,
        message: String,
        sessionId: String
    ): Result<AgentChatResponse> = runCatching {
        val request = AgentChatRequest(
            patientId = patientId,
            agentType = agentType,
            message = message,
            sessionId = sessionId
        )
        val response = apiService.chat(request)
        if (response.isSuccessful) {
            response.body()?.data ?: error("响应数据为空")
        } else {
            error("请求失败: ${response.code()}")
        }
    }

    suspend fun getRecommendedQuestions(
        patientId: String,
        agentType: String
    ): Result<List<String>> = runCatching {
        val response = apiService.getRecommendedQuestions(patientId, agentType)
        if (response.isSuccessful) {
            response.body()?.data?.questions ?: emptyList()
        } else {
            error("请求失败: ${response.code()}")
        }
    }
}
