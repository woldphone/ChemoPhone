package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.fragment.app.FragmentActivity
import com.example.service.BiometricAuthHelper
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.Constants
import com.example.data.LogEntry
import com.example.ui.MainViewModel
import com.example.ui.theme.BeaconAmber
import com.example.ui.theme.BeaconCyan
import com.example.ui.theme.BeaconCyanGlow
import com.example.ui.theme.BeaconEmerald
import com.example.ui.theme.BeaconNavyCard
import com.example.ui.theme.BeaconNavyDark
import com.example.ui.theme.BeaconRed
import com.example.ui.theme.BeaconRedDark
import com.example.ui.theme.BeaconTextLight
import com.example.ui.theme.BeaconTextMuted
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.loadPreferences(this)

        // Handle Back Press with Biometric challenge if Biometric shield is active
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val lockEnabled = prefs.getBoolean(Constants.KEY_BIOMETRIC_LOCK_ENABLED, Constants.DEFAULT_BIOMETRIC_LOCK_ENABLED)
                if (lockEnabled) {
                    BiometricAuthHelper.authenticate(
                        this@MainActivity,
                        "Exit Lock Screen",
                        "Authenticate with biometrics or PIN to exit Offline Phone Finder"
                    , onSuccess = {
                        // User authenticated, proceed with back action
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }, onFailure = {
                        Toast.makeText(this@MainActivity, "Exiting app is locked. Identity required.", Toast.LENGTH_SHORT).show()
                    })
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)

        setContent {
            MyApplicationTheme {
                FinderAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FinderAppScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as FragmentActivity

    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val isGattActive by viewModel.isGattActive.collectAsState()
    val isAcousticListening by viewModel.isAcousticListening.collectAsState()
    val isAlarmRinging by viewModel.isAlarmRinging.collectAsState()
    val verifiedTriggersCount by viewModel.verifiedTriggersCount.collectAsState()
    val rejectedAttemptsCount by viewModel.rejectedAttemptsCount.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val savedPasscodeHex by viewModel.passcodeHex.collectAsState()

    // Config preferences
    val mode by viewModel.acousticMode.collectAsState()
    val sensitivity by viewModel.acousticSensitivity.collectAsState()
    val sampleRate by viewModel.acousticSampleRate.collectAsState()
    val rhythmicCount by viewModel.acousticRhythmicCount.collectAsState()
    val strobeFreq by viewModel.strobeFrequency.collectAsState()
    val strobeOn by viewModel.strobeEnabled.collectAsState()
    val vibePattern by viewModel.vibrationPattern.collectAsState()
    val soundDuration by viewModel.alarmSoundDuration.collectAsState()
    val biometricLock by viewModel.biometricLockEnabled.collectAsState()
    val txPower by viewModel.gattTxPower.collectAsState()
    val advMode by viewModel.gattAdvMode.collectAsState()
    val showName by viewModel.gattShowName.collectAsState()

    var passcodeInput by remember(savedPasscodeHex) { mutableStateOf(savedPasscodeHex) }
    var hasPermissions by remember { mutableStateOf(checkRequiredPermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermissions = checkRequiredPermissions(context)
        if (!hasPermissions) {
            Toast.makeText(context, "Permissions are required for BLE advertising and audio alert", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(getRequiredPermissionsList())
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("finder_screen"),
        containerColor = BeaconNavyDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            HeaderBar()

            Spacer(modifier = Modifier.height(8.dp))
            AutoUpdateBanner(viewModel = viewModel)

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasPermissions) {
                PermissionWarningCard(
                    onRequestPermissions = {
                        permissionLauncher.launch(getRequiredPermissionsList())
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Card
                item {
                    StatusOverviewCard(
                        isServiceRunning = isServiceRunning,
                        isAdvertising = isAdvertising,
                        isGattActive = isGattActive,
                        isAcousticListening = isAcousticListening,
                        isAlarmRinging = isAlarmRinging,
                        verifiedCount = verifiedTriggersCount,
                        rejectedCount = rejectedAttemptsCount
                    )
                }

                // Emergency Stop Alarm Card
                if (isAlarmRinging) {
                    item {
                        EmergencyStopCard(
                            onStopAlarm = { viewModel.stopAlarm(context) }
                        )
                    }
                }

                // Service Toggle Card
                item {
                    ControlToggleCard(
                        title = "BLE Beacon & GATT Server",
                        subtitle = "Broadcast connectable BLE packets and host GATT verification server offline.",
                        icon = Icons.Default.Bluetooth,
                        isChecked = isServiceRunning,
                        accentColor = BeaconCyan,
                        testTag = "toggle_ble_service",
                        onCheckedChange = { enable ->
                            if (!hasPermissions) {
                                permissionLauncher.launch(getRequiredPermissionsList())
                            } else {
                                if (biometricLock && !enable) {
                                    BiometricAuthHelper.authenticate(
                                        activity,
                                        "Stop Finder Service",
                                        "Confirm biometric identity to disable phone finder",
                                        onSuccess = {
                                            viewModel.toggleService(context, false)
                                        },
                                        onFailure = { err ->
                                            Toast.makeText(context, "Authentication failed: $err", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    viewModel.toggleService(context, enable)
                                }
                            }
                        }
                    )
                }

                // Secondary Fail-Safe Acoustic Toggle Card
                item {
                    ControlToggleCard(
                        title = "Secondary Acoustic Finder",
                        subtitle = "Background mic listener for sharp clap/whistle peaks (no internet required).",
                        icon = Icons.Default.Mic,
                        isChecked = isAcousticListening,
                        accentColor = BeaconAmber,
                        testTag = "toggle_acoustic_detector",
                        onCheckedChange = { enable ->
                            if (!hasPermissions) {
                                permissionLauncher.launch(getRequiredPermissionsList())
                            } else {
                                if (biometricLock && !enable) {
                                    BiometricAuthHelper.authenticate(
                                        activity,
                                        "Disable Acoustic Finder",
                                        "Confirm biometric identity to disable microphone listening",
                                        onSuccess = {
                                            viewModel.toggleAcoustic(context, false)
                                        },
                                        onFailure = { err ->
                                            Toast.makeText(context, "Authentication failed: $err", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    if (!isServiceRunning && enable) {
                                        viewModel.toggleService(context, true)
                                    }
                                    viewModel.toggleAcoustic(context, enable)
                                }
                            }
                        }
                    )
                }

                // Security Passcode & GATT Details
                item {
                    SecurityConfigCard(
                        passcodeHex = passcodeInput,
                        onPasscodeChange = { passcodeInput = it },
                        onSavePasscode = {
                            viewModel.updatePasscode(context, passcodeInput)
                            Toast.makeText(context, "Passcode updated to 0x${passcodeInput.uppercase()}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Interactive Settings Customizer Screen
                item {
                    SettingsCustomizerCard(
                        viewModel = viewModel,
                        mode = mode,
                        sensitivity = sensitivity,
                        sampleRate = sampleRate,
                        rhythmicCount = rhythmicCount,
                        strobeFreq = strobeFreq,
                        strobeOn = strobeOn,
                        vibePattern = vibePattern,
                        soundDuration = soundDuration,
                        biometricLock = biometricLock,
                        txPower = txPower,
                        advMode = advMode,
                        showName = showName
                    )
                }

                // Test & Trigger Controls
                item {
                    TestControlsCard(
                        isRinging = isAlarmRinging,
                        onTestAlarm = { viewModel.testAlarm(context) },
                        onStopAlarm = { viewModel.stopAlarm(context) }
                    )
                }

                // How-To Discovery Guide
                item {
                    DiscoveryGuideCard(passcodeHex = savedPasscodeHex)
                }

                // System Logs Terminal
                item {
                    LogsTerminalCard(
                        logs = logs,
                        onClearLogs = { viewModel.clearLogs() }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun HeaderBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "OFFLINE PHONE FINDER",
                style = MaterialTheme.typography.labelSmall,
                color = BeaconCyanGlow,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Local Beacon & Security GATT",
                style = MaterialTheme.typography.titleMedium,
                color = BeaconTextLight,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BeaconNavyCard)
                .border(1.dp, BeaconCyan.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                contentDescription = "Beacon Icon",
                tint = BeaconCyan,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun StatusOverviewCard(
    isServiceRunning: Boolean,
    isAdvertising: Boolean,
    isGattActive: Boolean,
    isAcousticListening: Boolean,
    isAlarmRinging: Boolean,
    verifiedCount: Int,
    rejectedCount: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "beacon_pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BeaconNavyCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isAlarmRinging -> BeaconRed.copy(alpha = alphaAnim)
                                    isServiceRunning -> BeaconEmerald.copy(alpha = alphaAnim)
                                    else -> BeaconTextMuted
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isAlarmRinging -> "🚨 ALARM RINGING!"
                            isServiceRunning -> "BEACON ACTIVE"
                            else -> "STANDBY"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isAlarmRinging -> BeaconRed
                            isServiceRunning -> BeaconEmerald
                            else -> BeaconTextMuted
                        }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BeaconNavyDark
                ) {
                    Text(
                        text = "100% OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = BeaconCyanGlow,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusIndicatorItem(
                    label = "BLE Adv",
                    value = if (isAdvertising) "Active" else "Off",
                    isActive = isAdvertising
                )
                StatusIndicatorItem(
                    label = "GATT",
                    value = if (isGattActive) "Ready" else "Off",
                    isActive = isGattActive
                )
                StatusIndicatorItem(
                    label = "Acoustic",
                    value = if (isAcousticListening) "Listening" else "Off",
                    isActive = isAcousticListening
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BeaconNavyDark)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$verifiedCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BeaconEmerald
                    )
                    Text(
                        text = "Verified Triggers",
                        style = MaterialTheme.typography.bodySmall,
                        color = BeaconTextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(BeaconNavyCard)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$rejectedCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (rejectedCount > 0) BeaconRed else BeaconTextMuted
                    )
                    Text(
                        text = "Rejected Payloads",
                        style = MaterialTheme.typography.bodySmall,
                        color = BeaconTextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun StatusIndicatorItem(label: String, value: String, isActive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = BeaconTextMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isActive) BeaconCyanGlow else BeaconTextMuted
        )
    }
}

@Composable
fun EmergencyStopCard(onStopAlarm: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BeaconRedDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = BeaconTextLight,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ALARM SOUND & FLASH ACTIVE!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BeaconTextLight
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onStopAlarm,
                colors = ButtonDefaults.buttonColors(containerColor = BeaconRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("stop_alarm_button")
            ) {
                Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SILENCE ALARM NOW", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun ControlToggleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isChecked: Boolean,
    accentColor: Color,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BeaconNavyCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BeaconTextLight
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BeaconTextMuted,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BeaconTextLight,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = BeaconTextMuted,
                    uncheckedTrackColor = BeaconNavyDark
                )
            )
        }
    }
}

@Composable
fun SecurityConfigCard(
    passcodeHex: String,
    onPasscodeChange: (String) -> Unit,
    onSavePasscode: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BeaconNavyCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = BeaconCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GATT Security & Passcode Token",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BeaconTextLight
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Primary Service UUID:",
                style = MaterialTheme.typography.bodySmall,
                color = BeaconTextMuted
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BeaconNavyDark)
                    .clickable {
                        clipboardManager.setText(AnnotatedString(Constants.SERVICE_UUID.toString()))
                        Toast
                            .makeText(context, "Service UUID copied", Toast.LENGTH_SHORT)
                            .show()
                    }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Constants.SERVICE_UUID.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = BeaconCyanGlow,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "COPY",
                    style = MaterialTheme.typography.labelSmall,
                    color = BeaconTextMuted,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Write Characteristic UUID:",
                style = MaterialTheme.typography.bodySmall,
                color = BeaconTextMuted
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BeaconNavyDark)
                    .clickable {
                        clipboardManager.setText(AnnotatedString(Constants.CHARACTERISTIC_UUID.toString()))
                        Toast
                            .makeText(context, "Characteristic UUID copied", Toast.LENGTH_SHORT)
                            .show()
                    }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Constants.CHARACTERISTIC_UUID.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = BeaconCyanGlow,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "COPY",
                    style = MaterialTheme.typography.labelSmall,
                    color = BeaconTextMuted,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "4-Byte Security Pre-shared Passcode (Hex):",
                style = MaterialTheme.typography.bodySmall,
                color = BeaconTextMuted
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = passcodeHex,
                    onValueChange = { input ->
                        val filtered = input.uppercase().filter { it in "0123456789ABCDEF" }.take(8)
                        onPasscodeChange(filtered)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_passcode_hex"),
                    singleLine = true,
                    prefix = { Text("0x ", color = BeaconCyan, fontWeight = FontWeight.Bold) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = BeaconTextLight,
                        fontWeight = FontWeight.Bold
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BeaconCyan,
                        unfocusedBorderColor = BeaconNavyDark,
                        focusedContainerColor = BeaconNavyDark,
                        unfocusedContainerColor = BeaconNavyDark
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onSavePasscode,
                    enabled = passcodeHex.length == 8,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BeaconCyan),
                    modifier = Modifier.testTag("save_passcode_button")
                ) {
                    Text("SAVE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TestControlsCard(
    isRinging: Boolean,
    onTestAlarm: () -> Unit,
    onStopAlarm: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BeaconNavyCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Hardware Test Controls",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = BeaconTextLight
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTestAlarm,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("test_alarm_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BeaconCyan.copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BeaconCyan)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = BeaconCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Alarm", color = BeaconCyan, fontWeight = FontWeight.Bold)
                }

                if (isRinging) {
                    Button(
                        onClick = onStopAlarm,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("stop_alarm_card_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BeaconRed)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = BeaconTextLight, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop Alarm", color = BeaconTextLight, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveryGuideCard(passcodeHex: String) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BeaconNavyCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = BeaconCyanGlow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "How to Locate This Phone Offline",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BeaconTextLight
                    )
                }
                Text(
                    text = if (isExpanded) "Hide" else "Show Steps",
                    style = MaterialTheme.typography.labelMedium,
                    color = BeaconCyan
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    GuideStep(number = "1", title = "Use Any BLE Scanner App", description = "Open nRF Connect or any BLE scanner on another phone or device nearby.")
                    GuideStep(number = "2", title = "Scan for Service UUID", description = "Filter scan results for UUID: 12345678-abcd-1234-abcd-123456789abc.")
                    GuideStep(number = "3", title = "Connect Directly", description = "Tap Connect. Since device MAC addresses rotate, discovery relies strictly on the Service UUID.")
                    GuideStep(number = "4", title = "Write Security Token", description = "Locate Characteristic 87654321-abcd-4321-abcd-cba987654321. Write Byte Array payload 0x$passcodeHex.")
                    GuideStep(number = "5", title = "High-Volume Sound & Flash Trigger", description = "Phone will immediately override Silent/DND modes, play siren sound at max volume, and strobe the camera LED!")
                }
            }
        }
    }
}

@Composable
fun GuideStep(number: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(BeaconCyan.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, style = MaterialTheme.typography.labelSmall, color = BeaconCyan, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BeaconTextLight)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = BeaconTextMuted, lineHeight = 15.sp)
        }
    }
}

@Composable
fun LogsTerminalCard(logs: List<LogEntry>, onClearLogs: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BeaconNavyCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Local System Logs",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BeaconTextLight
                )
                if (logs.isNotEmpty()) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelSmall,
                        color = BeaconTextMuted,
                        modifier = Modifier.clickable { onClearLogs() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BeaconNavyDark)
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "No logs yet. Toggle BLE Service or trigger alarm to see real-time events.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BeaconTextMuted,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { log ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "[${log.timestamp}] ",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = BeaconTextMuted,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = log.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = when {
                                        log.isError -> BeaconRed
                                        log.isSuccess -> BeaconEmerald
                                        else -> BeaconTextLight
                                    },
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCustomizerCard(
    viewModel: MainViewModel,
    mode: String,
    sensitivity: Int,
    sampleRate: Int,
    rhythmicCount: Int,
    strobeFreq: Int,
    strobeOn: Boolean,
    vibePattern: String,
    soundDuration: Int,
    biometricLock: Boolean,
    txPower: String,
    advMode: String,
    showName: Boolean
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BeaconNavyCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = BeaconCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Customize App Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BeaconTextLight
                    )
                }
                Text(
                    text = if (isExpanded) "Hide" else "Show Settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = BeaconCyan
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // --- ACOUSTIC SETTINGS ---
                    Text(
                        text = "ACOUSTIC FINDER SETTINGS",
                        style = MaterialTheme.typography.labelSmall,
                        color = BeaconCyanGlow,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Mode Toggle (Rhythmic Code vs Peak Threshold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Acoustic Detection Mode", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                        Row {
                            Button(
                                onClick = { viewModel.updatePreference(context, Constants.KEY_ACOUSTIC_MODE, "rhythmic") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mode == "rhythmic") BeaconCyan else BeaconNavyDark
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp).padding(horizontal = 2.dp)
                            ) {
                                Text("Rhythmic", fontSize = 10.sp, color = if (mode == "rhythmic") BeaconNavyDark else BeaconTextMuted)
                            }
                            Button(
                                onClick = { viewModel.updatePreference(context, Constants.KEY_ACOUSTIC_MODE, "threshold") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mode == "threshold") BeaconCyan else BeaconNavyDark
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp).padding(horizontal = 2.dp)
                            ) {
                                Text("Peak", fontSize = 10.sp, color = if (mode == "threshold") BeaconNavyDark else BeaconTextMuted)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sensitivity Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Microphone Sensitivity ($sensitivity%)", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                            Text("100% = Whispers", style = MaterialTheme.typography.bodySmall, color = BeaconTextMuted)
                        }
                        Slider(
                            value = sensitivity.toFloat(),
                            onValueChange = { viewModel.updatePreference(context, Constants.KEY_ACOUSTIC_SENSITIVITY, it.toInt()) },
                            valueRange = 1f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = BeaconCyan,
                                activeTrackColor = BeaconCyan,
                                inactiveTrackColor = BeaconNavyDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Clap Count for Rhythmic Mode
                    if (mode == "rhythmic") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Rhythmic Spike Count", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                            Row {
                                Button(
                                    onClick = { viewModel.updatePreference(context, Constants.KEY_ACOUSTIC_RHYTHMIC_COUNT, 2) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (rhythmicCount == 2) BeaconCyan else BeaconNavyDark
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp).padding(horizontal = 2.dp)
                                ) {
                                    Text("Double-Clap", fontSize = 10.sp, color = if (rhythmicCount == 2) BeaconNavyDark else BeaconTextMuted)
                                }
                                Button(
                                    onClick = { viewModel.updatePreference(context, Constants.KEY_ACOUSTIC_RHYTHMIC_COUNT, 3) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (rhythmicCount == 3) BeaconCyan else BeaconNavyDark
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp).padding(horizontal = 2.dp)
                                ) {
                                    Text("Triple-Clap", fontSize = 10.sp, color = if (rhythmicCount == 3) BeaconNavyDark else BeaconTextMuted)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Sample rate (Power optimization)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Microphone Power Mode", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                        Row {
                            Button(
                                onClick = { viewModel.updatePreference(context, Constants.KEY_ACOUSTIC_SAMPLE_RATE, 16000) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sampleRate == 16000) BeaconCyan else BeaconNavyDark
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp).padding(horizontal = 2.dp)
                            ) {
                                Text("Low Power", fontSize = 10.sp, color = if (sampleRate == 16000) BeaconNavyDark else BeaconTextMuted)
                            }
                            Button(
                                onClick = { viewModel.updatePreference(context, Constants.KEY_ACOUSTIC_SAMPLE_RATE, 44100) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sampleRate == 44100) BeaconCyan else BeaconNavyDark
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp).padding(horizontal = 2.dp)
                            ) {
                                Text("High Precision", fontSize = 10.sp, color = if (sampleRate == 44100) BeaconNavyDark else BeaconTextMuted)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- GATT / ADV SETTINGS ---
                    Text(
                        text = "GATT & BEACON BROADCAST SETTINGS",
                        style = MaterialTheme.typography.labelSmall,
                        color = BeaconCyanGlow,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Show device name toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Broadcast Local Device Name", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                        Switch(
                            checked = showName,
                            onCheckedChange = { viewModel.updatePreference(context, Constants.KEY_GATT_SHOW_NAME, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BeaconTextLight,
                                checkedTrackColor = BeaconCyan,
                                uncheckedThumbColor = BeaconTextMuted,
                                uncheckedTrackColor = BeaconNavyDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tx Power level
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("GATT Signal Tx Power", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                        Row {
                            listOf("low", "medium", "high").forEach { pwr ->
                                Button(
                                    onClick = { viewModel.updatePreference(context, Constants.KEY_GATT_TX_POWER, pwr) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (txPower == pwr) BeaconCyan else BeaconNavyDark
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp).padding(horizontal = 2.dp)
                                ) {
                                    Text(pwr.uppercase(), fontSize = 9.sp, color = if (txPower == pwr) BeaconNavyDark else BeaconTextMuted)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Advertise Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("BLE Beacon Mode", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                        Row {
                            listOf("low_power", "balanced", "low_latency").forEach { modeKey ->
                                Button(
                                    onClick = { viewModel.updatePreference(context, Constants.KEY_GATT_ADV_MODE, modeKey) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (advMode == modeKey) BeaconCyan else BeaconNavyDark
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp).padding(horizontal = 2.dp)
                                ) {
                                    Text(modeKey.replace("_", " ").uppercase(), fontSize = 8.sp, color = if (advMode == modeKey) BeaconNavyDark else BeaconTextMuted)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- ALARM / FLASH SETTINGS ---
                    Text(
                        text = "ALARM SOUND & FLASH CUSTOMIZATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = BeaconCyanGlow,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Torch Strobe Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Flash Camera LED Strobe", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                        Switch(
                            checked = strobeOn,
                            onCheckedChange = { viewModel.updatePreference(context, Constants.KEY_STROBE_ENABLED, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BeaconTextLight,
                                checkedTrackColor = BeaconCyan,
                                uncheckedThumbColor = BeaconTextMuted,
                                uncheckedTrackColor = BeaconNavyDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Strobe speed
                    if (strobeOn) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Strobe Flashing Speed", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                            Row {
                                mapOf(100 to "Fast", 200 to "Normal", 500 to "Slow").forEach { (delayTime, label) ->
                                    Button(
                                        onClick = { viewModel.updatePreference(context, Constants.KEY_STROBE_FREQUENCY, delayTime) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (strobeFreq == delayTime) BeaconCyan else BeaconNavyDark
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp).padding(horizontal = 2.dp)
                                    ) {
                                        Text(label, fontSize = 9.sp, color = if (strobeFreq == delayTime) BeaconNavyDark else BeaconTextMuted)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Vibration pattern selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Vibration Pattern", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                        Row {
                            listOf("none", "pulse", "continuous", "sos").forEach { vMode ->
                                Button(
                                    onClick = { viewModel.updatePreference(context, Constants.KEY_VIBRATION_PATTERN, vMode) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (vibePattern == vMode) BeaconCyan else BeaconNavyDark
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp).padding(horizontal = 2.dp)
                                ) {
                                    Text(vMode.uppercase(), fontSize = 8.sp, color = if (vibePattern == vMode) BeaconNavyDark else BeaconTextMuted)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Alarm Duration Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Alarm Auto-Timeout ($soundDuration Secs)", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                            Text("Saves battery if lost", style = MaterialTheme.typography.bodySmall, color = BeaconTextMuted)
                        }
                        Slider(
                            value = soundDuration.toFloat(),
                            onValueChange = { viewModel.updatePreference(context, Constants.KEY_ALARM_SOUND_DURATION, it.toInt()) },
                            valueRange = 5f..120f,
                            colors = SliderDefaults.colors(
                                thumbColor = BeaconCyan,
                                activeTrackColor = BeaconCyan,
                                inactiveTrackColor = BeaconNavyDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- PRIVACY & BIOMETRIC LOCK ---
                    Text(
                        text = "SECURITY & CLOSING PRIVACY LOCK",
                        style = MaterialTheme.typography.labelSmall,
                        color = BeaconCyanGlow,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biometric Stop Shield", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                            Text("Requires fingerprint/face/passkey to close finders or stop background tasks", style = MaterialTheme.typography.bodySmall, color = BeaconTextMuted, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = biometricLock,
                            onCheckedChange = { viewModel.updatePreference(context, Constants.KEY_BIOMETRIC_LOCK_ENABLED, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BeaconTextLight,
                                checkedTrackColor = BeaconCyan,
                                uncheckedThumbColor = BeaconTextMuted,
                                uncheckedTrackColor = BeaconNavyDark
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AutoUpdateBanner(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val updateState by viewModel.updateStatus.collectAsState()

    // Automatically check for updates on startup
    LaunchedEffect(Unit) {
        viewModel.checkAppUpdates(scope)
    }

    when (val state = updateState) {
        is com.example.service.AutoUpdateManager.UpdateState.Checking -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BeaconNavyCard)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = BeaconCyan
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Checking for GitHub debug updates...", style = MaterialTheme.typography.bodySmall, color = BeaconTextMuted)
                }
            }
        }
        is com.example.service.AutoUpdateManager.UpdateState.UpdateAvailable -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BeaconCyan.copy(alpha = 0.15f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, BeaconCyan)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("New GitHub Debug Build Available!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = BeaconCyanGlow)
                        Text("Release tag: ${state.latestVersion}", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                    }
                    Button(
                        onClick = { viewModel.downloadAndInstallUpdate(context, scope, state.downloadUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = BeaconCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("UPDATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BeaconNavyDark)
                    }
                }
            }
        }
        is com.example.service.AutoUpdateManager.UpdateState.Downloading -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BeaconNavyCard)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Downloading latest debug APK...", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                        Text("${state.progress}%", style = MaterialTheme.typography.bodySmall, color = BeaconCyanGlow, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.progress.toFloat() / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = BeaconCyan,
                        trackColor = BeaconNavyDark
                    )
                }
            }
        }
        is com.example.service.AutoUpdateManager.UpdateState.ReadyToInstall -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BeaconEmerald.copy(alpha = 0.15f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, BeaconEmerald)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Download Complete!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = BeaconEmerald)
                        Text("Ready to launch package installer", style = MaterialTheme.typography.bodySmall, color = BeaconTextLight)
                    }
                    Button(
                        onClick = { com.example.service.AutoUpdateManager.launchInstaller(context, state.apkFile) },
                        colors = ButtonDefaults.buttonColors(containerColor = BeaconEmerald),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("INSTALL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BeaconNavyDark)
                    }
                }
            }
        }
        is com.example.service.AutoUpdateManager.UpdateState.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BeaconNavyCard)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Auto check: ${state.message}", style = MaterialTheme.typography.bodySmall, color = BeaconTextMuted, modifier = Modifier.weight(1f))
                    Button(
                        onClick = { viewModel.checkAppUpdates(scope) },
                        colors = ButtonDefaults.buttonColors(containerColor = BeaconNavyDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BeaconTextMuted),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("RETRY", fontSize = 10.sp, color = BeaconTextLight)
                    }
                }
            }
        }
        else -> {
            // Idle or NoUpdate - do not show anything to keep UI completely clean!
        }
    }
}

@Composable
fun PermissionWarningCard(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BeaconAmber.copy(alpha = 0.15f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, BeaconAmber)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = BeaconAmber,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BeaconAmber
                )
                Text(
                    text = "Bluetooth Advertise/Connect, Location, Audio, and Camera permissions are needed for local discovery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BeaconTextLight
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = BeaconAmber),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("GRANT", color = BeaconNavyDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun checkRequiredPermissions(context: Context): Boolean {
    val list = getRequiredPermissionsList()
    return list.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun getRequiredPermissionsList(): Array<String> {
    val list = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        list.add(Manifest.permission.BLUETOOTH_CONNECT)
        list.add(Manifest.permission.BLUETOOTH_SCAN)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return list.toTypedArray()
}
