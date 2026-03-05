package com.xinya.dtx.feature.pro.data

import com.xinya.dtx.core.network.ApiService
import com.xinya.dtx.core.network.dto.ProAnswer
import com.xinya.dtx.core.network.dto.ProQuestionListDto
import com.xinya.dtx.core.network.dto.ProSubmitRequest
import com.xinya.dtx.core.network.dto.ProSubmitResponse
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getQuestions(patientId: String): Result<ProQuestionListDto> = runCatching {
        val response = apiService.getProQuestions(patientId)
        if (response.isSuccessful) {
            response.body()?.data ?: ProQuestionListDto()
        } else {
            error("请求失败: ${response.code()}")
        }
    }

    suspend fun submitAnswers(
        patientId: String,
        answers: Map<String, Pair<String, Int>>
    ): Result<ProSubmitResponse> = runCatching {
        val proAnswers = answers.map { (questionId, answerScore) ->
            ProAnswer(
                questionId = questionId,
                answer = answerScore.first,
                score = answerScore.second
            )
        }
        val request = ProSubmitRequest(
            patientId = patientId,
            recordDate = LocalDate.now().toString(),
            answers = proAnswers
        )
        val response = apiService.submitPro(request)
        val body = response.body()
        when {
            !response.isSuccessful -> error("提交失败 (${response.code()})")
            body == null -> error("响应为空")
            body.code == 409 -> error("今日已打卡，请明天再来")
            body.code != 200 -> error(body.message ?: "提交失败 (${body.code})")
            else -> body.data ?: ProSubmitResponse(success = true)
        }
    }
}
