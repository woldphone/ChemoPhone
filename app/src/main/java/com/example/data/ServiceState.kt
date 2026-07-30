package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: String,
    val message: String,
    val isError: Boolean = false,
    val isSuccess: Boolean = false
)

object ServiceState {
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isGattActive = MutableStateFlow(false)
    val isGattActive: StateFlow<Boolean> = _isGattActive.asStateFlow()

    private val _isAcousticListening = MutableStateFlow(false)
    val isAcousticListening: StateFlow<Boolean> = _isAcousticListening.asStateFlow()

    private val _isAlarmRinging = MutableStateFlow(false)
    val isAlarmRinging: StateFlow<Boolean> = _isAlarmRinging.asStateFlow()

    private val _verifiedTriggersCount = MutableStateFlow(0)
    val verifiedTriggersCount: StateFlow<Int> = _verifiedTriggersCount.asStateFlow()

    private val _rejectedAttemptsCount = MutableStateFlow(0)
    val rejectedAttemptsCount: StateFlow<Int> = _rejectedAttemptsCount.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
        if (!running) {
            _isAdvertising.value = false
            _isGattActive.value = false
            _isAcousticListening.value = false
            _isAlarmRinging.value = false
        }
    }

    fun setAdvertising(advertising: Boolean) {
        _isAdvertising.value = advertising
    }

    fun setGattActive(active: Boolean) {
        _isGattActive.value = active
    }

    fun setAcousticListening(listening: Boolean) {
        _isAcousticListening.value = listening
    }

    fun setAlarmRinging(ringing: Boolean) {
        _isAlarmRinging.value = ringing
    }

    fun incrementVerifiedTriggers() {
        _verifiedTriggersCount.value += 1
    }

    fun incrementRejectedAttempts() {
        _rejectedAttemptsCount.value += 1
    }

    fun addLog(message: String, isError: Boolean = false, isSuccess: Boolean = false) {
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            message = message,
            isError = isError,
            isSuccess = isSuccess
        )
        val currentList = _logs.value.toMutableList()
        currentList.add(0, entry)
        if (currentList.size > 50) {
            currentList.removeAt(currentList.lastIndex)
        }
        _logs.value = currentList
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
