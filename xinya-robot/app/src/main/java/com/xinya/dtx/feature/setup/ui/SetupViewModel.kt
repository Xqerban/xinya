package com.xinya.dtx.feature.setup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinya.dtx.core.network.ApiService
import com.xinya.dtx.core.network.RobotBindRequest
import com.xinya.dtx.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val patientName: String = "",
    val error: String? = null
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun bind(patientId: String, bindCode: String) {
        if (patientId.isBlank() || bindCode.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "患者ID和绑定码不能为空")
            return
        }

        viewModelScope.launch {
            _uiState.value = SetupUiState(isLoading = true)
            try {
                val deviceId = sessionManager.ensureDeviceId()
                val request = RobotBindRequest(
                    deviceId = deviceId,
                    patientId = patientId,
                    bindCode = bindCode
                )
                val response = apiService.bindRobot(request)
                if (response.isSuccessful && response.body()?.code == 200) {
                    val data = response.body()!!.data!!
                    sessionManager.saveBindingInfo(
                        patientId = data.patientId,
                        patientName = data.patientName,
                        deviceToken = data.deviceToken
                    )
                    _uiState.value = SetupUiState(isSuccess = true, patientName = data.patientName)
                } else {
                    val msg = response.body()?.message ?: "绑定失败 (${response.code()})"
                    _uiState.value = SetupUiState(error = msg)
                }
            } catch (e: Exception) {
                _uiState.value = SetupUiState(error = e.message ?: "网络异常，请重试")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
