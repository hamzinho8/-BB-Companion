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
        initAudioTrack(service)

        if (isStreaming.get()) return

        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted, microphone transmission disabled, but playback remains active.")
            BridgeStateManager.logEvent("Microphone Android non autorisé - playback actif", EventType.WARNING)
            return
        }

        isStreaming.set(true)
        Log.d(TAG, "Starting CallAudioBridge VoIP streaming with BlackBerry...")
        BridgeStateManager.logEvent("Passerelle Voix IP BlackBerry active", EventType.INFO)

        val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            audioManager?.isMicrophoneMute = false
        } catch (e: Exception) {
            // ignore
        }

        recordJob = scope.launch {
            var audioRecord: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
                val bufferSize = maxOf(minBuf, CHUNK_SIZE * 4)

                // 1. Try VOICE_COMMUNICATION for hardware echo cancellation
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

                // 2. Fallback to standard MIC
                if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord?.release()
                    audioRecord = try {
                        AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            CHANNEL_IN,
                            ENCODING,
                            bufferSize
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                // 3. Fallback to DEFAULT
                if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord?.release()
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
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
                        delay(25) // ~40 packets per second
                    }
                } else {
                    Log.e(TAG, "AudioRecord could not be initialized with any audio source")
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

    /**
     * Plays voice packets received from BlackBerry microphone directly through Android voice communication stream
     */
    fun playIncomingVoice(context: Context, base64Data: String) {
        try {
            if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                initAudioTrack(context)
            }
            val pcmBytes = Base64.decode(base64Data, Base64.NO_WRAP)
            audioTrack?.write(pcmBytes, 0, pcmBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing incoming voice from BlackBerry", e)
        }
    }

    @Synchronized
    private fun initAudioTrack(context: Context) {
        if (audioTrack != null && audioTrack?.state == AudioTrack.STATE_INITIALIZED) return
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

            // Ensure voice call volume is audible
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val curVol = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                if (curVol < maxVol / 2) {
                    am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVol * 0.85).toInt(), 0)
                }
            }
            Log.d(TAG, "AudioTrack initialized successfully for VoIP incoming playback")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack", e)
        }
    }
}
