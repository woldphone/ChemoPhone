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

    // UI Configuration StateFlows
    val acousticMode = MutableStateFlow(Constants.DEFAULT_ACOUSTIC_MODE)
    val acousticSensitivity = MutableStateFlow(Constants.DEFAULT_ACOUSTIC_SENSITIVITY)
    val acousticSampleRate = MutableStateFlow(Constants.DEFAULT_ACOUSTIC_SAMPLE_RATE)
    val acousticRhythmicCount = MutableStateFlow(Constants.DEFAULT_ACOUSTIC_RHYTHMIC_COUNT)
    val strobeFrequency = MutableStateFlow(Constants.DEFAULT_STROBE_FREQUENCY)
    val strobeEnabled = MutableStateFlow(Constants.DEFAULT_STROBE_ENABLED)
    val vibrationPattern = MutableStateFlow(Constants.DEFAULT_VIBRATION_PATTERN)
    val alarmSoundDuration = MutableStateFlow(Constants.DEFAULT_ALARM_SOUND_DURATION)
    val biometricLockEnabled = MutableStateFlow(Constants.DEFAULT_BIOMETRIC_LOCK_ENABLED)
    val gattTxPower = MutableStateFlow(Constants.DEFAULT_GATT_TX_POWER)
    val gattAdvMode = MutableStateFlow(Constants.DEFAULT_GATT_ADV_MODE)
    val gattShowName = MutableStateFlow(Constants.DEFAULT_GATT_SHOW_NAME)

    fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        _passcodeHex.value = prefs.getString(Constants.KEY_PASSCODE_HEX, Constants.DEFAULT_PASSCODE_HEX) ?: Constants.DEFAULT_PASSCODE_HEX

        acousticMode.value = prefs.getString(Constants.KEY_ACOUSTIC_MODE, Constants.DEFAULT_ACOUSTIC_MODE) ?: Constants.DEFAULT_ACOUSTIC_MODE
        acousticSensitivity.value = prefs.getInt(Constants.KEY_ACOUSTIC_SENSITIVITY, Constants.DEFAULT_ACOUSTIC_SENSITIVITY)
        acousticSampleRate.value = prefs.getInt(Constants.KEY_ACOUSTIC_SAMPLE_RATE, Constants.DEFAULT_ACOUSTIC_SAMPLE_RATE)
        acousticRhythmicCount.value = prefs.getInt(Constants.KEY_ACOUSTIC_RHYTHMIC_COUNT, Constants.DEFAULT_ACOUSTIC_RHYTHMIC_COUNT)
        strobeFrequency.value = prefs.getInt(Constants.KEY_STROBE_FREQUENCY, Constants.DEFAULT_STROBE_FREQUENCY)
        strobeEnabled.value = prefs.getBoolean(Constants.KEY_STROBE_ENABLED, Constants.DEFAULT_STROBE_ENABLED)
        vibrationPattern.value = prefs.getString(Constants.KEY_VIBRATION_PATTERN, Constants.DEFAULT_VIBRATION_PATTERN) ?: Constants.DEFAULT_VIBRATION_PATTERN
        alarmSoundDuration.value = prefs.getInt(Constants.KEY_ALARM_SOUND_DURATION, Constants.DEFAULT_ALARM_SOUND_DURATION)
        biometricLockEnabled.value = prefs.getBoolean(Constants.KEY_BIOMETRIC_LOCK_ENABLED, Constants.DEFAULT_BIOMETRIC_LOCK_ENABLED)
        gattTxPower.value = prefs.getString(Constants.KEY_GATT_TX_POWER, Constants.DEFAULT_GATT_TX_POWER) ?: Constants.DEFAULT_GATT_TX_POWER
        gattAdvMode.value = prefs.getString(Constants.KEY_GATT_ADV_MODE, Constants.DEFAULT_GATT_ADV_MODE) ?: Constants.DEFAULT_GATT_ADV_MODE
        gattShowName.value = prefs.getBoolean(Constants.KEY_GATT_SHOW_NAME, Constants.DEFAULT_GATT_SHOW_NAME)
    }

    fun updatePreference(context: Context, key: String, value: Any) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        when (value) {
            is String -> {
                edit.putString(key, value)
                when (key) {
                    Constants.KEY_ACOUSTIC_MODE -> acousticMode.value = value
                    Constants.KEY_VIBRATION_PATTERN -> vibrationPattern.value = value
                    Constants.KEY_GATT_TX_POWER -> gattTxPower.value = value
                    Constants.KEY_GATT_ADV_MODE -> gattAdvMode.value = value
                }
            }
            is Int -> {
                edit.putInt(key, value)
                when (key) {
                    Constants.KEY_ACOUSTIC_SENSITIVITY -> acousticSensitivity.value = value
                    Constants.KEY_ACOUSTIC_SAMPLE_RATE -> acousticSampleRate.value = value
                    Constants.KEY_ACOUSTIC_RHYTHMIC_COUNT -> acousticRhythmicCount.value = value
                    Constants.KEY_STROBE_FREQUENCY -> strobeFrequency.value = value
                    Constants.KEY_ALARM_SOUND_DURATION -> alarmSoundDuration.value = value
                }
            }
            is Boolean -> {
                edit.putBoolean(key, value)
                when (key) {
                    Constants.KEY_STROBE_ENABLED -> strobeEnabled.value = value
                    Constants.KEY_BIOMETRIC_LOCK_ENABLED -> biometricLockEnabled.value = value
                    Constants.KEY_GATT_SHOW_NAME -> gattShowName.value = value
                }
            }
        }
        edit.apply()

        // Notify Service to dynamically reload preferences
        val intent = Intent(context, LocalBleService::class.java).apply {
            action = Constants.ACTION_UPDATE_DYNAMIC_PREFS
        }
        context.startService(intent)
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

    // Auto Update States
    val updateStatus = com.example.service.AutoUpdateManager.updateStatus

    fun checkAppUpdates(scope: kotlinx.coroutines.CoroutineScope) {
        com.example.service.AutoUpdateManager.checkForUpdates(scope)
    }

    fun downloadAndInstallUpdate(context: Context, scope: kotlinx.coroutines.CoroutineScope, downloadUrl: String) {
        com.example.service.AutoUpdateManager.downloadAndInstallApk(context, scope, downloadUrl)
    }

    fun clearLogs() {
        ServiceState.clearLogs()
    }
}
