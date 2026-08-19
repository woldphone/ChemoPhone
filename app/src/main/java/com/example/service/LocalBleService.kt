package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.Constants
import com.example.data.ServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.Arrays

class LocalBleService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null

    private lateinit var alarmController: AudioAlarmController
    private var acousticDetector: AcousticDetector? = null

    private var activePasscodeHex: String = Constants.DEFAULT_PASSCODE_HEX
    private var isAcousticEnabled: Boolean = false

    // Remote client session security state tracker to verify client successfully completed passcode verification
    private val authenticatedClients = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LocalBleService onCreate initialized")

        alarmController = AudioAlarmController(this)
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        loadPreferences()
        createNotificationChannel()

        ServiceState.setServiceRunning(true)
        ServiceState.addLog("Offline Phone Finder service initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "LocalBleService onStartCommand with action: $action")

        startForegroundWithNotification()

        when (action) {
            Constants.ACTION_START_SERVICE -> {
                startBleAdvertisingAndGattServer()
                syncAcousticDetectorState()
            }
            Constants.ACTION_STOP_SERVICE -> {
                stopBleAdvertisingAndGattServer()
                stopSelf()
                return START_NOT_STICKY
            }
            Constants.ACTION_TRIGGER_ALARM -> {
                ServiceState.addLog("Manual alarm test triggered from UI")
                alarmController.startAlarm(serviceScope)
                ServiceState.setAlarmRinging(true)
                updateNotification(isRinging = true)
            }
            Constants.ACTION_STOP_ALARM -> {
                ServiceState.addLog("Alarm stopped by user")
                alarmController.stopAlarm()
                ServiceState.setAlarmRinging(false)
                updateNotification(isRinging = false)
            }
            Constants.ACTION_TOGGLE_ACOUSTIC -> {
                isAcousticEnabled = intent?.getBooleanExtra(Constants.EXTRA_ACOUSTIC_ENABLED, false) ?: false
                saveAcousticPreference(isAcousticEnabled)
                syncAcousticDetectorState()
            }
            Constants.ACTION_UPDATE_PASSCODE -> {
                val newPasscode = intent?.getStringExtra(Constants.EXTRA_PASSCODE_HEX)
                if (!newPasscode.isNullOrBlank()) {
                    activePasscodeHex = newPasscode.uppercase().trim()
                    savePasscodePreference(activePasscodeHex)
                    ServiceState.addLog("Updated authentication passcode to 0x$activePasscodeHex")
                }
            }
            Constants.ACTION_UPDATE_DYNAMIC_PREFS -> {
                // Settings have changed, restart / apply settings dynamically
                if (ServiceState.isServiceRunning.value) {
                    stopBleAdvertisingAndGattServer()
                    startBleAdvertisingAndGattServer()
                }
                syncAcousticDetectorState()
            }
            else -> {
                startBleAdvertisingAndGattServer()
                syncAcousticDetectorState()
            }
        }

        return START_STICKY
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        activePasscodeHex = prefs.getString(Constants.KEY_PASSCODE_HEX, Constants.DEFAULT_PASSCODE_HEX) ?: Constants.DEFAULT_PASSCODE_HEX
        isAcousticEnabled = prefs.getBoolean(Constants.KEY_ACOUSTIC_ENABLED, false)
    }

    private fun savePasscodePreference(hex: String) {
        getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(Constants.KEY_PASSCODE_HEX, hex)
            .apply()
    }

    private fun saveAcousticPreference(enabled: Boolean) {
        getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(Constants.KEY_ACOUSTIC_ENABLED, enabled)
            .apply()
    }

    @SuppressLint("MissingPermission")
    private fun startBleAdvertisingAndGattServer() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            ServiceState.addLog("Bluetooth is disabled or unavailable on device", isError = true)
            return
        }

        // 1. Setup Connectable GATT Server
        setupGattServer()

        // 2. Setup BLE Advertiser
        bluetoothAdvertiser = bluetoothAdapter!!.bluetoothLeAdvertiser
        if (bluetoothAdvertiser == null) {
            ServiceState.addLog("BLE Advertiser is not supported on this device hardware", isError = true)
            return
        }

        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val advModeStr = prefs.getString(Constants.KEY_GATT_ADV_MODE, Constants.DEFAULT_GATT_ADV_MODE) ?: Constants.DEFAULT_GATT_ADV_MODE
        val txPowerStr = prefs.getString(Constants.KEY_GATT_TX_POWER, Constants.DEFAULT_GATT_TX_POWER) ?: Constants.DEFAULT_GATT_TX_POWER
        val showName = prefs.getBoolean(Constants.KEY_GATT_SHOW_NAME, Constants.DEFAULT_GATT_SHOW_NAME)

        val advMode = when (advModeStr) {
            "low_power" -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
            "low_latency" -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
            else -> AdvertiseSettings.ADVERTISE_MODE_BALANCED
        }

        val txPower = when (txPowerStr) {
            "ultra_low" -> AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW
            "low" -> AdvertiseSettings.ADVERTISE_TX_POWER_LOW
            "high" -> AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
            else -> AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(advMode)
            .setTxPowerLevel(txPower)
            .setConnectable(true) // Allow direct client handshake and discovery
            .setTimeout(0)
            .build()

        val pUuid = ParcelUuid(Constants.SERVICE_UUID)

        // Optimise BLE advertising data to include Service UUID and Device name correctly so scanner apps discover it easily
        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(showName)
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(pUuid)

        val data = dataBuilder.build()

        try {
            bluetoothAdvertiser?.startAdvertising(settings, data, advertiseCallback)
            ServiceState.addLog("Started Connectable BLE Advertising for UUID: ${Constants.SERVICE_UUID} (ShowName=$showName, TxPower=$txPowerStr)")
        } catch (e: Exception) {
            ServiceState.addLog("Failed to start BLE advertising: ${e.message}", isError = true)
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupGattServer() {
        if (gattServer != null) return

        gattServer = bluetoothManager?.openGattServer(this, gattServerCallback)
        if (gattServer == null) {
            ServiceState.addLog("Failed to open GATT Server", isError = true)
            return
        }

        val service = BluetoothGattService(
            Constants.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // Write-Only Trigger Characteristic
        val triggerCharacteristic = BluetoothGattCharacteristic(
            Constants.CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // Write Secure Command/Action Characteristic (Allows extension of commands via handshake + command byte)
        val cmdCharacteristic = BluetoothGattCharacteristic(
            Constants.COMMAND_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // Secure Read-Only Status Characteristic (battery, status, stats)
        val statusCharacteristic = BluetoothGattCharacteristic(
            Constants.STATUS_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(triggerCharacteristic)
        service.addCharacteristic(cmdCharacteristic)
        service.addCharacteristic(statusCharacteristic)

        val added = gattServer?.addService(service) ?: false
        if (added) {
            ServiceState.setGattActive(true)
            ServiceState.addLog("GATT Server initialized. Custom, commands and status characteristics are registered.")
        } else {
            ServiceState.addLog("Error adding Secure GATT Services to server", isError = true)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleAdvertisingAndGattServer() {
        try {
            bluetoothAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertiser: ${e.message}")
        }
        bluetoothAdvertiser = null
        ServiceState.setAdvertising(false)

        try {
            gattServer?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT server: ${e.message}")
        }
        gattServer = null
        ServiceState.setGattActive(false)
        ServiceState.addLog("BLE Advertiser and GATT Server stopped")
    }

    private fun syncAcousticDetectorState() {
        if (isAcousticEnabled) {
            if (acousticDetector == null) {
                acousticDetector = AcousticDetector(this) { reason ->
                    ServiceState.addLog("Sound Trigger: $reason", isSuccess = true)
                    alarmController.startAlarm(serviceScope)
                    ServiceState.setAlarmRinging(true)
                    updateNotification(isRinging = true)
                }
            }
            acousticDetector?.startListening(serviceScope)
            ServiceState.setAcousticListening(true)
            ServiceState.addLog("Secondary Acoustic Detector active (listening for claps/whistles)")
        } else {
            acousticDetector?.stopListening()
            acousticDetector = null
            ServiceState.setAcousticListening(false)
            ServiceState.addLog("Acoustic Detector disabled")
        }
    }

    // GATT Server Callback Handling
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        /**
         * Triggered when a remote client device connects or disconnects from this GATT server.
         */
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            val address = device?.address ?: "Unknown"
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                ServiceState.addLog("Remote device connected via BLE: $address")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                ServiceState.addLog("Remote device disconnected: $address")
                authenticatedClients.remove(address)
            }
        }

        /**
         * Support Reading Status information after handshake verification.
         */
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            Log.d(TAG, "GATT Read Request received on characteristic ${characteristic?.uuid}")

            val address = device?.address ?: ""
            if (characteristic?.uuid == Constants.STATUS_CHARACTERISTIC_UUID) {
                // GATT Security check: ensure client successfully solved handshake before returning telemetry
                if (authenticatedClients.contains(address)) {
                    val statusString = "BAT:85|ALM:${if (alarmController.isRinging()) 1 else 0}|TRG:${ServiceState.verifiedTriggersCount.value}"
                    val dataBytes = statusString.toByteArray(Charsets.UTF_8)
                    val slicedBytes = if (offset < dataBytes.size) dataBytes.copyOfRange(offset, dataBytes.size) else byteArrayOf()
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slicedBytes)
                    ServiceState.addLog("Telemetry Status requested and sent over BLE.")
                } else {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, byteArrayOf())
                    ServiceState.addLog("Security status read rejected for unauthenticated client device: $address", isError = true)
                }
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, byteArrayOf())
            }
        }

        /**
         * Triggered when a remote client device writes to our secure characteristics.
         * Performs cryptographic/pre-shared token handshake validation.
         */
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

            Log.d(TAG, "GATT Write Request received on characteristic ${characteristic?.uuid}")

            var isValid = false
            val expectedBytes = hexStringToByteArray(activePasscodeHex)

            val address = device?.address ?: ""
            if (characteristic?.uuid == Constants.CHARACTERISTIC_UUID && value != null) {
                if (Arrays.equals(value, expectedBytes)) {
                    isValid = true
                }

                if (responseNeeded) {
                    val statusCode = if (isValid) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
                    gattServer?.sendResponse(device, requestId, statusCode, offset, value)
                }

                if (isValid) {
                    authenticatedClients.add(address)
                    ServiceState.incrementVerifiedTriggers()
                    ServiceState.addLog(
                        "VALID SECURITY HANDSHAKE (0x${activePasscodeHex})! Triggering high-volume alarm!",
                        isSuccess = true
                    )
                    alarmController.startAlarm(serviceScope)
                    ServiceState.setAlarmRinging(true)
                    updateNotification(isRinging = true)
                } else {
                    ServiceState.incrementRejectedAttempts()
                    val receivedHex = value?.joinToString("") { "%02X".format(it) } ?: "null"
                    ServiceState.addLog(
                        "SECURITY REJECTED: Invalid trigger payload 0x$receivedHex",
                        isError = true
                    )
                }
                return
            }

            // Secure Extensible Config/Command Characteristic UUID (passcode + command byte + dynamic payload)
            if (characteristic?.uuid == Constants.COMMAND_CHARACTERISTIC_UUID && value != null) {
                // Must have at least 5 bytes (4 bytes passcode + 1 byte command code)
                if (value.size >= 5) {
                    val receivedPasscode = value.copyOfRange(0, 4)
                    if (Arrays.equals(receivedPasscode, expectedBytes)) {
                        isValid = true
                        authenticatedClients.add(address)
                        val commandCode = value[4]

                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                        }

                        ServiceState.incrementVerifiedTriggers()

                        when (commandCode) {
                            Constants.CMD_TRIGGER_FULL_ALARM -> {
                                ServiceState.addLog("BLE Command: Trigger Full Alarm", isSuccess = true)
                                alarmController.startAlarm(serviceScope)
                                ServiceState.setAlarmRinging(true)
                                updateNotification(isRinging = true)
                            }
                            Constants.CMD_SILENCE_ALARM -> {
                                ServiceState.addLog("BLE Command: Silence Alarm", isSuccess = true)
                                alarmController.stopAlarm()
                                ServiceState.setAlarmRinging(false)
                                updateNotification(isRinging = false)
                            }
                            Constants.CMD_STROBE_ONLY -> {
                                ServiceState.addLog("BLE Command: Trigger Strobe LED Only", isSuccess = true)
                                alarmController.startAlarm(serviceScope, strobeOnly = true)
                                ServiceState.setAlarmRinging(true)
                                updateNotification(isRinging = true)
                            }
                            Constants.CMD_SOUND_ONLY -> {
                                ServiceState.addLog("BLE Command: Trigger Sound Only", isSuccess = true)
                                alarmController.startAlarm(serviceScope, soundOnly = true)
                                ServiceState.setAlarmRinging(true)
                                updateNotification(isRinging = true)
                            }
                            Constants.CMD_UPDATE_SETTINGS -> {
                                val extraPayload = if (value.size > 5) value.copyOfRange(5, value.size).joinToString("") { "%02X".format(it) } else "none"
                                ServiceState.addLog("BLE Command: Settings update request ($extraPayload)", isSuccess = true)
                            }
                            Constants.CMD_CUSTOM_ACTION -> {
                                val customVal = if (value.size > 5) value[5].toInt() else 0
                                ServiceState.addLog("BLE Command: Executed Extensible Custom Action #$customVal", isSuccess = true)
                            }
                            else -> {
                                ServiceState.addLog("BLE Command: Unknown Command Code $commandCode received.", isError = true)
                            }
                        }
                    }
                }

                if (!isValid) {
                    ServiceState.incrementRejectedAttempts()
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, value)
                    }
                    val receivedHex = value.joinToString("") { "%02X".format(it) }
                    ServiceState.addLog("BLE Command Rejected: Unauthorized payload 0x$receivedHex", isError = true)
                }
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            ServiceState.setAdvertising(true)
            ServiceState.addLog("BLE Advertising successfully broadcasting")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            ServiceState.setAdvertising(false)
            val errorMsg = when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                else -> "Code $errorCode"
            }
            ServiceState.addLog("BLE Advertising failed: $errorMsg", isError = true)
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification(isRinging = alarmController.isRinging())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    (if (isAcousticEnabled) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            startForeground(NOTIFICATION_ID, notification, foregroundType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(isRinging: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(isRinging))
    }

    private fun buildNotification(isRinging: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopAlarmIntent = Intent(this, LocalBleService::class.java).apply {
            action = Constants.ACTION_STOP_ALARM
        }
        val pendingStopAlarm = PendingIntent.getService(
            this, 1, stopAlarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isRinging) "🚨 ALARM TRIGGERED - PHONE FOUND!" else "📡 Local Phone Beacon Active"
        val text = if (isRinging) "Tap STOP ALARM to silence device sound and flash" else "BLE Beacon & Security GATT Server broadcasting offline"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (isRinging) {
            builder.addAction(0, "STOP ALARM", pendingStopAlarm)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Offline Phone Finder Service"
            val descriptionText = "Persistent local offline beacon and acoustic finder listener"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "LocalBleService onDestroy")
        stopBleAdvertisingAndGattServer()
        acousticDetector?.stopListening()
        alarmController.stopAlarm()
        ServiceState.setServiceRunning(false)
        ServiceState.addLog("Service stopped")
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hexStringToByteArray(hex: String): ByteArray {
        val s = hex.replace("0x", "").replace(" ", "").uppercase()
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }

    companion object {
        private const val TAG = "LocalBleService"
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "offline_phone_finder_channel"
    }
}
