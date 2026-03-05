package com.xinya.dtx.feature.hopetree.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinya.dtx.core.network.dto.HopeTreeStatusResponse
import com.xinya.dtx.core.session.SessionManager
import com.xinya.dtx.feature.hopetree.data.HopeTreeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HopeTreeUiState(
    val isLoading: Boolean = false,
    val status: HopeTreeStatusResponse? = null,
    val levelUpMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class HopeTreeViewModel @Inject constructor(
    private val hopeTreeRepository: HopeTreeRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HopeTreeUiState(isLoading = true))
    val uiState: StateFlow<HopeTreeUiState> = _uiState.asStateFlow()

    init {
        loadStatus()
    }

    fun loadStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val patientId = sessionManager.patientId.first()
            hopeTreeRepository.getStatus(patientId).fold(
                onSuccess = { status ->
                    _uiState.value = HopeTreeUiState(status = status)
                },
                onFailure = { e ->
                    _uiState.value = HopeTreeUiState(error = e.message)
                }
            )
        }
    }

    fun dismissLevelUp() {
        _uiState.value = _uiState.value.copy(levelUpMessage = null)
    }
}
