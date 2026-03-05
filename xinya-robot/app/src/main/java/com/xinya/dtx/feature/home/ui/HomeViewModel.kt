package com.xinya.dtx.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinya.dtx.core.network.dto.PatientDto
import com.xinya.dtx.core.session.SessionManager
import com.xinya.dtx.feature.home.data.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val patient: PatientDto? = null,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadPatient()
    }

    fun loadPatient() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val patientId = sessionManager.patientId.first()
            patientRepository.getPatient(patientId).fold(
                onSuccess = { patient ->
                    _uiState.value = HomeUiState(isLoading = false, patient = patient)
                },
                onFailure = { e ->
                    _uiState.value = HomeUiState(isLoading = false, error = e.message)
                }
            )
        }
    }
}
