package com.xinya.dtx.feature.education.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinya.dtx.core.network.dto.EducationContentDto
import com.xinya.dtx.feature.education.data.EducationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EducationUiState(
    val isLoading: Boolean = false,
    val contents: List<EducationContentDto> = emptyList(),
    val selectedCategory: String = "全部",
    val error: String? = null
)

@HiltViewModel
class EducationViewModel @Inject constructor(
    private val educationRepository: EducationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EducationUiState(isLoading = true))
    val uiState: StateFlow<EducationUiState> = _uiState.asStateFlow()

    private var allContents: List<EducationContentDto> = emptyList()

    init {
        loadContents()
    }

    fun loadContents() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            educationRepository.getContents().fold(
                onSuccess = { contents ->
                    allContents = contents
                    applyFilter(_uiState.value.selectedCategory)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        applyFilter(category)
    }

    private fun applyFilter(category: String) {
        val filtered = if (category == "全部") allContents
                       else allContents.filter { it.category == category }
        _uiState.value = _uiState.value.copy(isLoading = false, contents = filtered)
    }
}
