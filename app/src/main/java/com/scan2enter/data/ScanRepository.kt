package com.scan2enter.data
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ScanRepository {

    private val _lastCode = MutableStateFlow("")

    val lastCode: StateFlow<String>
        get() = _lastCode

    fun updateCode(code: String) {
        _lastCode.value = code
    }

    fun getCode(): String {
        return _lastCode.value
    }

    fun clear() {
        _lastCode.value = ""
    }
}