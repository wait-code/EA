package com.examreader.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 后台扫描任务的全局状态：服务写，任何界面（顶部横幅）读 */
sealed class ScanState {
    data object Idle : ScanState()

    data class Running(val done: Int, val total: Int, val message: String) : ScanState()

    data class Success(val paperId: String, val title: String) : ScanState()

    data class Failed(val message: String) : ScanState()
}

object ScanJobBus {
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    fun publish(value: ScanState) {
        _state.value = value
    }

    fun idle() {
        _state.value = ScanState.Idle
    }
}
