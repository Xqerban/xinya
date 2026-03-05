package com.xinya.dtx.feature.agent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinya.dtx.core.session.SessionManager
import com.xinya.dtx.feature.agent.data.AgentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatMessageUi(
    val content: String,
    val isFromUser: Boolean,
    val isLoading: Boolean = false
)

data class AgentUiState(
    val messages: List<ChatMessageUi> = emptyList(),
    val recommendedQuestions: List<String> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val crisisAlert: Boolean = false
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private val sessionId = UUID.randomUUID().toString()

    fun initialize(agentType: String) {
        val greeting = if (agentType == "psych")
            "您好，我是小芽，您的心理陪护伙伴。今天感觉怎么样？"
        else
            "您好，我是小护士，您的护理宣教伙伴。有什么护理方面的问题想了解吗？"

        _uiState.value = AgentUiState(
            messages = listOf(ChatMessageUi(greeting, false))
        )
        loadRecommendedQuestions(agentType)
    }

    private fun loadRecommendedQuestions(agentType: String) {
        viewModelScope.launch {
            val patientId = sessionManager.patientId.first()
            agentRepository.getRecommendedQuestions(patientId, agentType).onSuccess { questions ->
                _uiState.value = _uiState.value.copy(recommendedQuestions = questions)
            }
        }
    }

    fun sendMessage(agentType: String, message: String) {
        if (message.isBlank() || _uiState.value.isSending) return

        val userMessage = ChatMessageUi(message, true)
        val loadingMessage = ChatMessageUi("", false, isLoading = true)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage + loadingMessage,
            isSending = true,
            error = null,
            recommendedQuestions = emptyList()
        )

        viewModelScope.launch {
            val patientId = sessionManager.patientId.first()
            agentRepository.chat(patientId, agentType, message, sessionId).fold(
                onSuccess = { response ->
                    val messagesWithoutLoading = _uiState.value.messages.dropLast(1)
                    _uiState.value = _uiState.value.copy(
                        messages = messagesWithoutLoading + ChatMessageUi(response.reply, false),
                        isSending = false,
                        recommendedQuestions = response.recommendedQuestions,
                        crisisAlert = response.crisisAlert
                    )
                },
                onFailure = { e ->
                    val messagesWithoutLoading = _uiState.value.messages.dropLast(1)
                    _uiState.value = _uiState.value.copy(
                        messages = messagesWithoutLoading,
                        isSending = false,
                        error = e.message ?: "发送失败，请重试"
                    )
                }
            )
        }
    }
}
