package com.example.service

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioAlarmController(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var torchJob: Job? = null
    private var durationJob: Job? = null
    private var vibrator: Vibrator? = null
    private var originalAlarmVolume: Int = 0
    private var originalMusicVolume: Int = 0
    private var isAlarmActive = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    init {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun getStrobeFrequency(): Long {
        val prefs = context.getSharedPreferences(com.example.data.Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(com.example.data.Constants.KEY_STROBE_FREQUENCY, com.example.data.Constants.DEFAULT_STROBE_FREQUENCY).toLong()
    }

    private fun isStrobeEnabled(): Boolean {
        val prefs = context.getSharedPreferences(com.example.data.Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(com.example.data.Constants.KEY_STROBE_ENABLED, com.example.data.Constants.DEFAULT_STROBE_ENABLED)
    }

    private fun getVibrationPattern(): String {
        val prefs = context.getSharedPreferences(com.example.data.Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(com.example.data.Constants.KEY_VIBRATION_PATTERN, com.example.data.Constants.DEFAULT_VIBRATION_PATTERN) ?: com.example.data.Constants.DEFAULT_VIBRATION_PATTERN
    }

    private fun getAlarmDuration(): Int {
        val prefs = context.getSharedPreferences(com.example.data.Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(com.example.data.Constants.KEY_ALARM_SOUND_DURATION, com.example.data.Constants.DEFAULT_ALARM_SOUND_DURATION)
    }

    @Synchronized
    fun startAlarm(scope: CoroutineScope, strobeOnly: Boolean = false, soundOnly: Boolean = false) {
        if (isAlarmActive) return
        isAlarmActive = true

        Log.d(TAG, "Triggering Custom Alarm (strobeOnly=$strobeOnly, soundOnly=$soundOnly)!")

        val alarmDurationSecs = getAlarmDuration()

        // 1. Maximize Volume on ALARM and MUSIC channels (only if not strobeOnly)
        if (!strobeOnly) {
            try {
                originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                originalMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to adjust volume streams: ${e.message}")
            }

            // 2. Play Looping Alarm Sound via MediaPlayer
            try {
                var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                }
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting MediaPlayer: ${e.message}")
            }

            // 3. Trigger Vibration based on custom pattern setting
            val patternType = getVibrationPattern()
            if (patternType != "none") {
                try {
                    val timings = when (patternType) {
                        "continuous" -> longArrayOf(0, 1000)
                        "sos" -> longArrayOf(0, 200, 100, 200, 100, 200, 300, 600, 200, 600, 200, 600, 300, 200, 100, 200, 100, 200, 500)
                        else -> longArrayOf(0, 400, 200, 400) // "pulse"
                    }
                    val amplitudes = if (patternType == "continuous") intArrayOf(0, 255) else {
                        IntArray(timings.size) { index -> if (index % 2 == 1) 255 else 0 }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(timings, 0)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Vibration failed: ${e.message}")
                }
            }
        }

        // 4. Flash Camera LED Torch in a strobe loop (if not soundOnly and strobe is enabled)
        if (!soundOnly && isStrobeEnabled()) {
            val strobeFreq = getStrobeFrequency()
            torchJob = scope.launch(Dispatchers.IO) {
                try {
                    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                        val characteristics = cameraManager.getCameraCharacteristics(id)
                        val hasFlash = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)
                        hasFlash == true
                    }

                    if (cameraId != null) {
                        var torchState = false
                        while (isActive && isAlarmActive) {
                            torchState = !torchState
                            try {
                                cameraManager.setTorchMode(cameraId, torchState)
                            } catch (e: Exception) {
                                Log.e(TAG, "Torch toggle error: ${e.message}")
                            }
                            delay(strobeFreq)
                        }
                        // Turn off camera torch when loop exits
                        try {
                            cameraManager.setTorchMode(cameraId, false)
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Camera Flash Strobe failed: ${e.message}")
                }
            }
        }

        // 5. Autoshutdown timer for alarm duration
        durationJob = scope.launch(Dispatchers.Main) {
            delay(alarmDurationSecs * 1000L)
            if (isAlarmActive) {
                com.example.data.ServiceState.addLog("Alarm automatically timed out after $alarmDurationSecs seconds")
                stopAlarm()
                com.example.data.ServiceState.setAlarmRinging(false)
            }
        }
    }

    @Synchronized
    fun stopAlarm() {
        if (!isAlarmActive) return
        isAlarmActive = false

        Log.d(TAG, "Stopping Sound Override Alarm...")

        // Stop Autocancel Timer Job
        try {
            durationJob?.cancel()
            durationJob = null
        } catch (_: Exception) {}

        // Stop Sound
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer: ${e.message}")
        }

        // Stop Strobe LED
        torchJob?.cancel()
        torchJob = null
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, false)
            }
        } catch (_: Exception) {}

        // Stop Vibration
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}

        // Restore Volume Streams
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalMusicVolume, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring volumes: ${e.message}")
        }
    }

    fun isRinging(): Boolean = isAlarmActive

    companion object {
        private const val TAG = "AudioAlarmController"
    }
}
