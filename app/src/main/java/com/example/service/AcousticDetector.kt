package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AcousticDetector(
    private val context: Context,
    private val onSoundTriggered: (reason: String) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isListening = false

    private fun getSampleRate(): Int {
        val prefs = context.getSharedPreferences(com.example.data.Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(com.example.data.Constants.KEY_ACOUSTIC_SAMPLE_RATE, com.example.data.Constants.DEFAULT_ACOUSTIC_SAMPLE_RATE)
    }

    private fun getAcousticMode(): String {
        val prefs = context.getSharedPreferences(com.example.data.Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(com.example.data.Constants.KEY_ACOUSTIC_MODE, com.example.data.Constants.DEFAULT_ACOUSTIC_MODE) ?: com.example.data.Constants.DEFAULT_ACOUSTIC_MODE
    }

    private fun getSensitivity(): Int {
        val prefs = context.getSharedPreferences(com.example.data.Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(com.example.data.Constants.KEY_ACOUSTIC_SENSITIVITY, com.example.data.Constants.DEFAULT_ACOUSTIC_SENSITIVITY)
    }

    private fun getRhythmicCount(): Int {
        val prefs = context.getSharedPreferences(com.example.data.Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(com.example.data.Constants.KEY_ACOUSTIC_RHYTHMIC_COUNT, com.example.data.Constants.DEFAULT_ACOUSTIC_RHYTHMIC_COUNT)
    }

    @SuppressLint("MissingPermission")
    fun startListening(scope: CoroutineScope) {
        if (isListening) return

        val sRate = getSampleRate()
        val mode = getAcousticMode()
        val sensitivity = getSensitivity()
        val rhythmicCount = getRhythmicCount()

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sRate, channelConfig, audioFormat)
            .coerceAtLeast(2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sRate,
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
                var ambientBaseline = 1500.0

                // Rhythmic clap/whistle tracking list
                val spikeTimestamps = mutableListOf<Long>()

                // Calculate numeric threshold based on sensitivity slider (1-100)
                // Higher sensitivity means LOWER required peak threshold to trigger.
                // 1 sensitivity -> peak required ~ 30000
                // 100 sensitivity -> peak required ~ 4000
                val mappedPeakThreshold = 30000 - ((sensitivity.coerceIn(1, 100) - 1) * 260)

                Log.d(TAG, "AcousticDetector started. Mode=$mode, Mapped Peak Threshold=$mappedPeakThreshold, Sample Rate=$sRate")

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
                        
                        // Dynamically smooth ambient baseline to auto-calibrate to ambient noise floors
                        ambientBaseline = ambientBaseline * 0.95 + rms * 0.05

                        val now = System.currentTimeMillis()

                        // A spike is recognized if the peak is above the sensitivity threshold and at least 3.5x baseline
                        if (maxPeak > mappedPeakThreshold && maxPeak > ambientBaseline * 3.5) {
                            if (mode == "rhythmic") {
                                // For rhythmic claps, prevent logging multiple spikes for the same continuous sound (debounce 300ms)
                                val lastSpike = spikeTimestamps.lastOrNull() ?: 0L
                                if (now - lastSpike > 300) {
                                    spikeTimestamps.add(now)
                                    Log.d(TAG, "Rhythmic Spike detected ($maxPeak)! Total Spikes in window: ${spikeTimestamps.size}")
                                }

                                // Clear spikes older than 2.5 seconds (the rhythmic window)
                                spikeTimestamps.removeAll { now - it > 2500 }

                                // If required clap count (e.g., 2 for double clap, 3 for triple clap) is reached in the window:
                                if (spikeTimestamps.size >= rhythmicCount) {
                                    spikeTimestamps.clear()
                                    Log.d(TAG, "Rhythmic Multi-Spike Pattern Matched successfully!")
                                    onSoundTriggered("Acoustic Rhythmic Code Match ($rhythmicCount claps/whistles)")
                                }
                            } else {
                                // Threshold Mode - require a higher peak/longer debounce to avoid voice triggers
                                val lastSpike = spikeTimestamps.lastOrNull() ?: 0L
                                if (now - lastSpike > 3000) { // 3-second debounce
                                    spikeTimestamps.add(now)
                                    Log.d(TAG, "Threshold peak detected: peak=$maxPeak, threshold=$mappedPeakThreshold")
                                    onSoundTriggered("Acoustic Peak Threshold Exceeded ($maxPeak)")
                                }
                            }
                        }
                    }
                    // Tiny delay to be highly battery-friendly
                    delay(5)
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
