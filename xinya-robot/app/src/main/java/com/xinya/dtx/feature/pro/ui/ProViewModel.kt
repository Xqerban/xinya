package com.xinya.dtx.feature.pro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinya.dtx.core.network.dto.ProQuestionDto
import com.xinya.dtx.core.session.SessionManager
import com.xinya.dtx.feature.pro.data.ProRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProUiState(
    val isLoading: Boolean = false,
    val questions: List<ProQuestionDto> = emptyList(),
    val currentStep: Int = 0,
    // key: questionId, value: Pair(answerText, score)
    val answers: Map<String, Pair<String, Int>> = emptyMap(),
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val alreadyCheckedIn: Boolean = false,  // 今日已打卡
    val error: String? = null,
    val energyDelta: Int = 0
)

@HiltViewModel
class ProViewModel @Inject constructor(
    private val proRepository: ProRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProUiState(isLoading = true))
    val uiState: StateFlow<ProUiState> = _uiState.asStateFlow()

    init {
        loadQuestions()
    }

    private fun loadQuestions() {
        viewModelScope.launch {
            val patientId = sessionManager.patientId.first()
            proRepository.getQuestions(patientId).fold(
                onSuccess = { result ->
                    if (result.checkedInToday) {
                        _uiState.value = ProUiState(alreadyCheckedIn = true)
                    } else {
                        _uiState.value = ProUiState(questions = result.questions)
                    }
                },
                onFailure = { e ->
                    _uiState.value = ProUiState(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun selectAnswer(questionId: String, answer: String, score: Int) {
        _uiState.value = _uiState.value.copy(
            answers = _uiState.value.answers + (questionId to Pair(answer, score))
        )
    }

    fun nextStep() {
        val state = _uiState.value
        if (state.currentStep < state.questions.size - 1) {
            _uiState.value = state.copy(currentStep = state.currentStep + 1)
        }
    }

    fun prevStep() {
        val state = _uiState.value
        if (state.currentStep > 0) {
            _uiState.value = state.copy(currentStep = state.currentStep - 1)
        }
    }

    fun submit() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            val patientId = sessionManager.patientId.first()
            proRepository.submitAnswers(patientId, _uiState.value.answers).fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        isSubmitted = true,
                        energyDelta = response.psychEnergyDelta
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = e.message ?: "提交失败，请重试"
                    )
                }
            )
        }
    }
}
