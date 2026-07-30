package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.example.data.Constants
import com.example.data.ServiceState
import com.example.service.LocalBleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {

    val isServiceRunning = ServiceState.isServiceRunning
    val isAdvertising = ServiceState.isAdvertising
    val isGattActive = ServiceState.isGattActive
    val isAcousticListening = ServiceState.isAcousticListening
    val isAlarmRinging = ServiceState.isAlarmRinging
    val verifiedTriggersCount = ServiceState.verifiedTriggersCount
    val rejectedAttemptsCount = ServiceState.rejectedAttemptsCount
    val logs = ServiceState.logs

    private val _passcodeHex = MutableStateFlow(Constants.DEFAULT_PASSCODE_HEX)
    val passcodeHex: StateFlow<String> = _passcodeHex.asStateFlow()

    fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        _passcodeHex.value = prefs.getString(Constants.KEY_PASSCODE_HEX, Constants.DEFAULT_PASSCODE_HEX) ?: Constants.DEFAULT_PASSCODE_HEX
    }

    fun toggleService(context: Context, enable: Boolean) {
        val intent = Intent(context, LocalBleService::class.java).apply {
            action = if (enable) Constants.ACTION_START_SERVICE else Constants.ACTION_STOP_SERVICE
        }
        if (enable) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    fun toggleAcoustic(context: Context, enable: Boolean) {
        val intent = Intent(context, LocalBleService::class.java).apply {
            action = Constants.ACTION_TOGGLE_ACOUSTIC
            putExtra(Constants.EXTRA_ACOUSTIC_ENABLED, enable)
        }
        context.startService(intent)
    }

    fun updatePasscode(context: Context, hex: String) {
        val cleanHex = hex.replace("0x", "").replace(" ", "").uppercase().trim()
        if (cleanHex.length == 8) { // 4 bytes = 8 hex chars
            _passcodeHex.value = cleanHex
            val intent = Intent(context, LocalBleService::class.java).apply {
                action = Constants.ACTION_UPDATE_PASSCODE
                putExtra(Constants.EXTRA_PASSCODE_HEX, cleanHex)
            }
            context.startService(intent)
        }
    }

    fun testAlarm(context: Context) {
        val intent = Intent(context, LocalBleService::class.java).apply {
            action = Constants.ACTION_TRIGGER_ALARM
        }
        context.startService(intent)
    }

    fun stopAlarm(context: Context) {
        val intent = Intent(context, LocalBleService::class.java).apply {
            action = Constants.ACTION_STOP_ALARM
        }
        context.startService(intent)
    }

    fun clearLogs() {
        ServiceState.clearLogs()
    }
}
