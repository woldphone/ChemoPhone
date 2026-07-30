package com.example.service

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AcousticDetector(
    private val onSoundTriggered: (reason: String) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isListening = false

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        .coerceAtLeast(2048)

    @SuppressLint("MissingPermission")
    fun startListening(scope: CoroutineScope) {
        if (isListening) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
            isListening = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(1024)
                var lastTriggerTime = 0L
                var ambientBaseline = 1500.0

                while (isActive && isListening) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        var sumSquare = 0.0
                        var maxPeak = 0

                        for (i in 0 until readSize) {
                            val sample = buffer[i].toInt()
                            sumSquare += sample * sample
                            val absVal = kotlin.math.abs(sample)
                            if (absVal > maxPeak) {
                                maxPeak = absVal
                            }
                        }

                        val rms = sqrt(sumSquare / readSize)
                        
                        // Dynamically smooth ambient baseline
                        ambientBaseline = ambientBaseline * 0.95 + rms * 0.05

                        val now = System.currentTimeMillis()
                        // Peak threshold for sharp clap/whistle: peak > 16000 AND peak > 4x baseline
                        if (maxPeak > 16000 && maxPeak > ambientBaseline * 4.0 && (now - lastTriggerTime > 3000)) {
                            lastTriggerTime = now
                            Log.d(TAG, "Acoustic sharp peak detected: peak=$maxPeak, rms=$rms, baseline=$ambientBaseline")
                            onSoundTriggered("Acoustic Peak Detected (Clap/Whistle Spike: $maxPeak)")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AcousticDetector: ${e.message}", e)
            stopListening()
        }
    }

    fun stopListening() {
        isListening = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AcousticDetector"
    }
}
