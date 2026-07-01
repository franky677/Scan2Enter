package com.scan2enter.viewmodel



import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scan2enter.data.ScanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val lastCode: String = ""
)
class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())

    val uiState: StateFlow<MainUiState> =
        _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ScanRepository.lastCode.collect { code ->
                _uiState.value =
                    _uiState.value.copy(lastCode = code)
            }
        }
    }

    fun clearCode() {
        ScanRepository.clear()
    }

    fun onQrScanned(code: String) {
        ScanRepository.updateCode(code)
    }
    }
