package com.xinya.dtx.feature.agent.data

import com.google.gson.Gson
import com.xinya.dtx.core.network.ApiService
import com.xinya.dtx.core.network.dto.AgentChatRequest
import com.xinya.dtx.core.network.dto.AgentChatResponse
import com.xinya.dtx.core.network.dto.RecommendedQuestionsResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import javax.inject.Inject
import javax.inject.Singleton

sealed class ChatStreamEvent {
    data class Start(val sessionId: String? = null) : ChatStreamEvent()
    data class Delta(val content: String, val stage: String? = null) : ChatStreamEvent()
    data class Done(val response: AgentChatResponse) : ChatStreamEvent()
    data class Error(val message: String) : ChatStreamEvent()
}

@Singleton
class AgentRepository @Inject constructor(
    private val apiService: ApiService,
    private val okHttpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val gson = Gson()

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

    /**
     * psych（小芽）流式对话：调用后端 /api/agent/chat/stream（SSE）。
     */
    fun chatStream(
        patientId: String,
        agentType: String,
        message: String,
        sessionId: String,
    ): Flow<ChatStreamEvent> = callbackFlow {
        val payload = AgentChatRequest(
            patientId = patientId,
            agentType = agentType,
            message = message,
            sessionId = sessionId
        )

        val json = gson.toJson(payload)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        val url = apiServiceBaseUrl().trimEnd('/') + "/api/agent/chat/stream"
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "text/event-stream")
            .build()

        val call = okHttpClient.newCall(request)

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                trySend(ChatStreamEvent.Error("请求失败: ${response.code}"))
                response.close()
                close()
                return@callbackFlow
            }

            val source = response.body?.source()
            if (source == null) {
                trySend(ChatStreamEvent.Error("响应体为空"))
                response.close()
                close()
                return@callbackFlow
            }

            readSse(source) { event, data ->
                when (event) {
                    "start" -> trySend(ChatStreamEvent.Start())
                    "delta" -> {
                        val obj = runCatching { gson.fromJson(data, Map::class.java) }.getOrNull()
                        val content = (obj?.get("content") as? String).orEmpty()
                        val stage = obj?.get("stage") as? String
                        if (content.isNotEmpty()) {
                            trySend(ChatStreamEvent.Delta(content = content, stage = stage))
                        }
                    }
                    "done" -> {
                        val doneObj = runCatching { gson.fromJson(data, AgentChatResponse::class.java) }.getOrNull()
                        if (doneObj != null) {
                            trySend(ChatStreamEvent.Done(doneObj))
                        } else {
                            trySend(ChatStreamEvent.Error("done 解析失败"))
                        }
                        // done 后结束
                        close()
                    }
                    "error" -> {
                        val msg = runCatching { gson.fromJson(data, Map::class.java)?.get("message") as? String }
                            .getOrNull()
                        trySend(ChatStreamEvent.Error(msg ?: "服务端错误"))
                        close()
                    }
                }
            }

            response.close()
        } catch (e: Exception) {
            trySend(ChatStreamEvent.Error(e.message ?: "网络错误"))
            close()
        }

        awaitClose { call.cancel() }
    }.flowOn(ioDispatcher)

    /**
     * Retrofit 的 baseUrl 在构建时已固定，这里从 OkHttp 的配置复用同一个 baseUrl（BuildConfig.API_BASE_URL）。
     */
    private fun apiServiceBaseUrl(): String {
        // 与 di/AppModule.kt 中 Retrofit baseUrl 保持一致
        return com.xinya.dtx.di.BuildConfig.API_BASE_URL
    }

    private fun readSse(source: BufferedSource, onEvent: (event: String, data: String) -> Unit) {
        var event: String? = null
        val dataLines = ArrayList<String>()

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank()) {
                // event block end
                val e = event
                if (e != null) {
                    val data = dataLines.joinToString("\n")
                    onEvent(e, data)
                }
                event = null
                dataLines.clear()
                continue
            }

            when {
                line.startsWith("event:") -> event = line.substringAfter("event:").trim()
                line.startsWith("data:") -> dataLines.add(line.substringAfter("data:").trim())
            }
        }
    }
}
