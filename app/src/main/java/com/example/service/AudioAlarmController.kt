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

    @Synchronized
    fun startAlarm(scope: CoroutineScope) {
        if (isAlarmActive) return
        isAlarmActive = true

        Log.d(TAG, "Triggering High-Volume Sound & Strobe LED Override Alarm!")

        // 1. Maximize Volume on ALARM and MUSIC channels
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

        // 3. Trigger Vibration
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 500, 200, 500)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500), 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed: ${e.message}")
        }

        // 4. Flash Camera LED Torch in a strobe loop
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
                        delay(200)
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

    @Synchronized
    fun stopAlarm() {
        if (!isAlarmActive) return
        isAlarmActive = false

        Log.d(TAG, "Stopping Sound Override Alarm...")

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
