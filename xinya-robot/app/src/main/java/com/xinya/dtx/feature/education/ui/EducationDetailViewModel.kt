package com.xinya.dtx.feature.education.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinya.dtx.core.network.dto.EducationContentDto
import com.xinya.dtx.core.session.SessionManager
import com.xinya.dtx.feature.education.data.EducationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EducationDetailUiState(
    val isLoading: Boolean = true,
    val content: EducationContentDto? = null,
    val error: String? = null,
    val isCompleted: Boolean = false,
    val isReporting: Boolean = false,
    val rewardExp: Int = 0,
    val showCompletionToast: Boolean = false
)

@HiltViewModel
class EducationDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val educationRepository: EducationRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val contentId: String = checkNotNull(savedStateHandle["contentId"])

    private val _uiState = MutableStateFlow(EducationDetailUiState())
    val uiState: StateFlow<EducationDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            educationRepository.getContentDetail(contentId).fold(
                onSuccess = { content ->
                    _uiState.value = _uiState.value.copy(isLoading = false, content = content)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun markAsCompleted(watchedSeconds: Int = 0) {
        if (_uiState.value.isCompleted || _uiState.value.isReporting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isReporting = true)
            val patientId = sessionManager.patientId.first()
            if (patientId.isBlank()) {
                _uiState.value = _uiState.value.copy(isReporting = false)
                return@launch
            }
            educationRepository.reportProgress(
                patientId = patientId,
                contentId = contentId,
                watchedSeconds = watchedSeconds,
                completed = true
            ).fold(
                onSuccess = { resp ->
                    _uiState.value = _uiState.value.copy(
                        isReporting = false,
                        isCompleted = true,
                        rewardExp = resp.hopeTreeExpDelta,
                        showCompletionToast = true
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isReporting = false)
                }
            )
        }
    }

    fun dismissCompletionToast() {
        _uiState.value = _uiState.value.copy(showCompletionToast = false)
    }
}
