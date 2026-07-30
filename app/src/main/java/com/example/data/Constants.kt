package com.example.data

import java.util.UUID

object Constants {
    // Primary Service UUID for discovery
    val SERVICE_UUID: UUID = UUID.fromString("12345678-abcd-1234-abcd-123456789abc")
    
    // Custom Write-Only Characteristic UUID for secure trigger
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("87654321-abcd-4321-abcd-cba987654321")
    
    // Default 4-byte pre-shared hex security passcode token: 0xDE, 0xAD, 0xBE, 0xEF
    val DEFAULT_PASSCODE_BYTES: ByteArray = byteArrayOf(
        0xDE.toByte(),
        0xAD.toByte(),
        0xBE.toByte(),
        0xEF.toByte()
    )

    // Intent Actions
    const val ACTION_START_SERVICE = "com.example.offlinephonefinder.ACTION_START_SERVICE"
    const val ACTION_STOP_SERVICE = "com.example.offlinephonefinder.ACTION_STOP_SERVICE"
    const val ACTION_TRIGGER_ALARM = "com.example.offlinephonefinder.ACTION_TRIGGER_ALARM"
    const val ACTION_STOP_ALARM = "com.example.offlinephonefinder.ACTION_STOP_ALARM"
    const val ACTION_TOGGLE_ACOUSTIC = "com.example.offlinephonefinder.ACTION_TOGGLE_ACOUSTIC"
    const val ACTION_UPDATE_PASSCODE = "com.example.offlinephonefinder.ACTION_UPDATE_PASSCODE"

    const val EXTRA_ACOUSTIC_ENABLED = "extra_acoustic_enabled"
    const val EXTRA_PASSCODE_HEX = "extra_passcode_hex"

    // Preferences
    const val PREFS_NAME = "phone_finder_prefs"
    const val KEY_PASSCODE_HEX = "key_passcode_hex"
    const val KEY_ACOUSTIC_ENABLED = "key_acoustic_enabled"
    const val DEFAULT_PASSCODE_HEX = "DEADBEEF"
}
