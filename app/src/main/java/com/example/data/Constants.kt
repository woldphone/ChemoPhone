package com.example.data

import java.util.UUID

object Constants {
    // Primary Service UUID for discovery
    val SERVICE_UUID: UUID = UUID.fromString("12345678-abcd-1234-abcd-123456789abc")
    
    // Custom Write-Only Characteristic UUID for secure trigger (requires passcode prepended/appended)
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("87654321-abcd-4321-abcd-cba987654321")

    // Secure Extensible Config/Command Characteristic UUID (passcode + command byte + dynamic payload)
    val COMMAND_CHARACTERISTIC_UUID: UUID = UUID.fromString("87654321-abcd-4321-abcd-cba987654322")

    // Secure Read-Only Status Characteristic (readable after successful handshake authentication)
    val STATUS_CHARACTERISTIC_UUID: UUID = UUID.fromString("87654321-abcd-4321-abcd-cba987654323")
    
    // Default 4-byte pre-shared hex security passcode token: 0xDE, 0xAD, 0xBE, 0xEF
    val DEFAULT_PASSCODE_BYTES: ByteArray = byteArrayOf(
        0xDE.toByte(),
        0xAD.toByte(),
        0xBE.toByte(),
        0xEF.toByte()
    )

    // Command/Action Constants (Handled by COMMAND_CHARACTERISTIC_UUID)
    const val CMD_TRIGGER_FULL_ALARM = 0x01.toByte()
    const val CMD_SILENCE_ALARM = 0x02.toByte()
    const val CMD_STROBE_ONLY = 0x03.toByte()
    const val CMD_SOUND_ONLY = 0x04.toByte()
    const val CMD_UPDATE_SETTINGS = 0x05.toByte()
    const val CMD_CUSTOM_ACTION = 0x06.toByte()

    // Intent Actions
    const val ACTION_START_SERVICE = "com.example.offlinephonefinder.ACTION_START_SERVICE"
    const val ACTION_STOP_SERVICE = "com.example.offlinephonefinder.ACTION_STOP_SERVICE"
    const val ACTION_TRIGGER_ALARM = "com.example.offlinephonefinder.ACTION_TRIGGER_ALARM"
    const val ACTION_STOP_ALARM = "com.example.offlinephonefinder.ACTION_STOP_ALARM"
    const val ACTION_TOGGLE_ACOUSTIC = "com.example.offlinephonefinder.ACTION_TOGGLE_ACOUSTIC"
    const val ACTION_UPDATE_PASSCODE = "com.example.offlinephonefinder.ACTION_UPDATE_PASSCODE"
    const val ACTION_UPDATE_DYNAMIC_PREFS = "com.example.offlinephonefinder.ACTION_UPDATE_DYNAMIC_PREFS"

    const val EXTRA_ACOUSTIC_ENABLED = "extra_acoustic_enabled"
    const val EXTRA_PASSCODE_HEX = "extra_passcode_hex"

    // Preferences Keys
    const val PREFS_NAME = "phone_finder_prefs"
    const val KEY_PASSCODE_HEX = "key_passcode_hex"
    const val KEY_ACOUSTIC_ENABLED = "key_acoustic_enabled"
    const val KEY_ACOUSTIC_MODE = "key_acoustic_mode" // "rhythmic" or "threshold"
    const val KEY_ACOUSTIC_SENSITIVITY = "key_acoustic_sensitivity" // 1 to 100
    const val KEY_ACOUSTIC_SAMPLE_RATE = "key_acoustic_sample_rate" // "16000" or "44100"
    const val KEY_ACOUSTIC_RHYTHMIC_COUNT = "key_acoustic_rhythmic_count" // 2 or 3 claps
    const val KEY_STROBE_FREQUENCY = "key_strobe_frequency" // milliseconds delay
    const val KEY_STROBE_ENABLED = "key_strobe_enabled"
    const val KEY_VIBRATION_PATTERN = "key_vibration_pattern" // "continuous", "pulse", "sos", "none"
    const val KEY_ALARM_SOUND_DURATION = "key_alarm_sound_duration" // seconds
    const val KEY_BIOMETRIC_LOCK_ENABLED = "key_biometric_lock_enabled"
    const val KEY_GATT_TX_POWER = "key_gatt_tx_power" // "low", "medium", "high"
    const val KEY_GATT_ADV_MODE = "key_gatt_adv_mode" // "low_power", "balanced", "low_latency"
    const val KEY_GATT_SHOW_NAME = "key_gatt_show_name"

    // Defaults
    const val DEFAULT_PASSCODE_HEX = "DEADBEEF"
    const val DEFAULT_ACOUSTIC_MODE = "rhythmic"
    const val DEFAULT_ACOUSTIC_SENSITIVITY = 50
    const val DEFAULT_ACOUSTIC_SAMPLE_RATE = 16000
    const val DEFAULT_ACOUSTIC_RHYTHMIC_COUNT = 2
    const val DEFAULT_STROBE_FREQUENCY = 200
    const val DEFAULT_STROBE_ENABLED = true
    const val DEFAULT_VIBRATION_PATTERN = "pulse"
    const val DEFAULT_ALARM_SOUND_DURATION = 30
    const val DEFAULT_BIOMETRIC_LOCK_ENABLED = false
    const val DEFAULT_GATT_TX_POWER = "high"
    const val DEFAULT_GATT_ADV_MODE = "balanced"
    const val DEFAULT_GATT_SHOW_NAME = true
}
