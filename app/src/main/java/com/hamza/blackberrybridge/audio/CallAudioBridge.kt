package com.hamza.blackberrybridge.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.state.EventType
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance VoIP audio streaming bridge over Bluetooth RFCOMM.
 * Allows BlackBerry (Curve 9300 / Bold / Torch) without native Bluetooth HFP audio profiles
 * to stream microphone and earpiece voice data directly through the application's Bluetooth connection.
 */
object CallAudioBridge {
    private const val TAG = "CallAudioBridge"
    private const val SAMPLE_RATE = 8000
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private const val CHUNK_SIZE = 320 // 20ms of 8000Hz 16-bit Mono (160 samples * 2 bytes)

    private val isStreaming = AtomicBoolean(false)
    private var recordJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun startStreaming(service: BluetoothService) {
        if (isStreaming.get()) return

        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted, skipping VoIP stream")
            return
        }

        isStreaming.set(true)
        Log.d(TAG, "Starting CallAudioBridge VoIP streaming with BlackBerry...")
        BridgeStateManager.logEvent("Passerelle Voix IP BlackBerry démarrée", EventType.INFO)

        initAudioTrack(service)

        recordJob = scope.launch {
            var audioRecord: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
                val bufferSize = maxOf(minBuf, CHUNK_SIZE * 4)

                // Try VOICE_COMMUNICATION first for AEC (acoustic echo cancellation)
                audioRecord = try {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE,
                        CHANNEL_IN,
                        ENCODING,
                        bufferSize
                    )
                } catch (e: Exception) {
                    null
                }

                if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord?.release()
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_IN,
                        ENCODING,
                        bufferSize
                    )
                }

                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording()
                    val pcmBuffer = ByteArray(CHUNK_SIZE)

                    while (isStreaming.get() && isActive) {
                        val read = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
                        if (read > 0) {
                            val b64 = Base64.encodeToString(pcmBuffer, 0, read, Base64.NO_WRAP)
                            service.sendPacket(BSBPacket("VOICE_TX", listOf(b64)))
                        }
                        delay(25) // ~40 packets per second (smooth voice transmission)
                    }
                } else {
                    Log.e(TAG, "AudioRecord could not be initialized")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during audio streaming recording loop", e)
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun stopStreaming() {
        if (!isStreaming.getAndSet(false)) return
        Log.d(TAG, "Stopping CallAudioBridge VoIP stream...")
        recordJob?.cancel()
        recordJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            // ignore
        }
        BridgeStateManager.logEvent("Passerelle Voix IP BlackBerry arrêtée", EventType.INFO)
    }

    fun playIncomingVoice(base64Data: String) {
        if (!isStreaming.get()) return
        try {
            val pcmBytes = Base64.decode(base64Data, Base64.NO_WRAP)
            audioTrack?.write(pcmBytes, 0, pcmBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing incoming voice from BlackBerry", e)
        }
    }

    private fun initAudioTrack(context: Context) {
        try {
            val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_OUT)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBuf, CHUNK_SIZE * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack", e)
        }
    }
}
