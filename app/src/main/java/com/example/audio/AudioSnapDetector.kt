package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * High-performance on-device acoustic transient detector for finger snaps, claps, and clicks.
 * Runs completely locally with zero cloud dependencies.
 */
class AudioSnapDetector(private val context: Context) {

    private val tag = "AudioSnapDetector"
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _currentDb = MutableStateFlow(30f)
    val currentDb = _currentDb.asStateFlow()

    private val _baselineDb = MutableStateFlow(35f)
    val baselineDb = _baselineDb.asStateFlow()

    // Configurable thresholds
    var snapThresholdDb = MutableStateFlow(72f) // Minimum dB to trigger
    var deltaTriggerDb = MutableStateFlow(18f)  // Sudden jump above ambient noise floor

    // Flow of snap events with the detected peak decibel
    private val _snapEvents = MutableSharedFlow<Float>(extraBufferCapacity = 5)
    val snapEvents = _snapEvents.asSharedFlow()

    private var lastTriggerTimeMs = 0L
    private val debounceWindowMs = 280L

    fun startListening(): Boolean {
        if (_isListening.value) return true

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(tag, "RECORD_AUDIO permission not granted")
            return false
        }

        try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            _isListening.value = true

            recordingJob = scope.launch {
                val buffer = ShortArray(1024)
                var smoothedNoiseFloor = 40.0f

                while (isActive && _isListening.value) {
                    val readSamples = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readSamples > 0) {
                        // Calculate RMS Amplitude
                        var sumSquares = 0.0
                        for (i in 0 until readSamples) {
                            val sample = buffer[i].toDouble()
                            sumSquares += sample * sample
                        }
                        val rms = sqrt(sumSquares / readSamples)

                        // Normalize to 0-100 dB scale
                        // 32767 is max short value
                        val normalizedRms = (rms / 32767.0).coerceAtLeast(1e-5)
                        val calculatedDb = (20.0 * log10(normalizedRms) + 100.0).toFloat().coerceIn(0f, 100f)

                        _currentDb.value = calculatedDb

                        // Slowly adapt ambient noise floor (EMA)
                        if (calculatedDb < smoothedNoiseFloor + 5f) {
                            smoothedNoiseFloor = (smoothedNoiseFloor * 0.96f) + (calculatedDb * 0.04f)
                        } else {
                            smoothedNoiseFloor = (smoothedNoiseFloor * 0.995f) + (calculatedDb * 0.005f)
                        }
                        _baselineDb.value = smoothedNoiseFloor

                        val now = System.currentTimeMillis()
                        val delta = calculatedDb - smoothedNoiseFloor

                        // Check if this is a sharp transient spike (snap / click)
                        val isSpike = delta >= deltaTriggerDb.value && calculatedDb >= snapThresholdDb.value
                        val isDebounced = (now - lastTriggerTimeMs) > debounceWindowMs

                        if (isSpike && isDebounced) {
                            lastTriggerTimeMs = now
                            _snapEvents.tryEmit(calculatedDb)
                            Log.d(tag, "Snap Detected! dB: $calculatedDb, Delta: $delta")
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(tag, "Error starting AudioRecord", e)
            _isListening.value = false
            return false
        }
    }

    fun stopListening() {
        _isListening.value = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping audioRecord", e)
        }
        audioRecord = null
    }
}
