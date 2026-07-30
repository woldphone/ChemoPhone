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

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true) // Allow client devices to connect directly
            .setTimeout(0) // Continuous advertising loop
            .build()

        val pUuid = ParcelUuid(Constants.SERVICE_UUID)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(pUuid)
            .build()

        try {
            bluetoothAdvertiser?.startAdvertising(settings, data, advertiseCallback)
            ServiceState.addLog("Started Connectable BLE Advertising for UUID: ${Constants.SERVICE_UUID}")
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

        // Write-Only Characteristic with security permission
        val characteristic = BluetoothGattCharacteristic(
            Constants.CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(characteristic)
        val added = gattServer?.addService(service) ?: false
        if (added) {
            ServiceState.setGattActive(true)
            ServiceState.addLog("Gatt Server initialized with Write Characteristic: ${Constants.CHARACTERISTIC_UUID}")
        } else {
            ServiceState.addLog("Error adding GATT Service to server", isError = true)
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
                acousticDetector = AcousticDetector { reason ->
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
            }
        }

        /**
         * Triggered when a remote client device writes to our secure Write-Only Characteristic.
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

            if (characteristic?.uuid == Constants.CHARACTERISTIC_UUID && value != null) {
                if (Arrays.equals(value, expectedBytes)) {
                    isValid = true
                }
            }

            // Send GATT Response if client requested response
            if (responseNeeded) {
                val statusCode = if (isValid) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
                gattServer?.sendResponse(device, requestId, statusCode, offset, value)
            }

            if (isValid) {
                ServiceState.incrementVerifiedTriggers()
                ServiceState.addLog(
                    "VALID SECURITY TOKEN RECEIVED (0x${activePasscodeHex})! Triggering high-volume alarm!",
                    isSuccess = true
                )
                alarmController.startAlarm(serviceScope)
                ServiceState.setAlarmRinging(true)
                updateNotification(isRinging = true)
            } else {
                ServiceState.incrementRejectedAttempts()
                val receivedHex = value?.joinToString("") { "%02X".format(it) } ?: "null"
                ServiceState.addLog(
                    "SECURITY REJECTED: Invalid payload 0x$receivedHex from ${device?.address}",
                    isError = true
                )
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
