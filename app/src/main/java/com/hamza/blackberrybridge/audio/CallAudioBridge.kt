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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-performance VoIP audio streaming bridge over Bluetooth RFCOMM.
 * Allows BlackBerry (Curve 9300 / Bold / Torch) without native Bluetooth HFP audio profiles
 * to stream microphone and earpiece voice data directly through the application's Bluetooth connection.
 */
object CallAudioBridge {
    private const val TAG = "CallAudioBridge"
    const val SAMPLE_RATE = 8000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private const val CHUNK_SIZE = 320 // 20ms of 8000Hz 16-bit Mono (160 samples * 2 bytes)

    private val isStreaming = AtomicBoolean(false)
    private var recordJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _txPackets = MutableStateFlow(0)
    val txPackets: StateFlow<Int> = _txPackets.asStateFlow()

    private val _rxPackets = MutableStateFlow(0)
    val rxPackets: StateFlow<Int> = _rxPackets.asStateFlow()

    private val _isVoipEnabled = MutableStateFlow(false) // Default false = SmartWatch mode (handsfree on phone, zero echo, zero crash)
    val isVoipEnabled: StateFlow<Boolean> = _isVoipEnabled.asStateFlow()

    private val _isBridgeActive = MutableStateFlow(false)
    val isBridgeActive: StateFlow<Boolean> = _isBridgeActive.asStateFlow()

    fun setVoipEnabled(enabled: Boolean) {
        _isVoipEnabled.value = enabled
        if (!enabled && isStreaming.get()) {
            stopStreaming()
        }
    }

    fun startStreaming(service: BluetoothService) {
        if (!_isVoipEnabled.value) {
            Log.d(TAG, "SmartWatch mode active: VoIP streaming disabled to prevent Bluetooth bandwidth saturation and echo.")
            return
        }

        initAudioTrack(service)

        if (isStreaming.get()) return

        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted, microphone transmission disabled, but playback remains active.")
            BridgeStateManager.logEvent("Microphone Android non autorisé - playback actif", EventType.WARNING)
            return
        }

        isStreaming.set(true)
        _isBridgeActive.value = true
        _txPackets.value = 0
        _rxPackets.value = 0

        Log.d(TAG, "Starting CallAudioBridge VoIP streaming with BlackBerry...")
        BridgeStateManager.logEvent("Passerelle Voix IP active (8000Hz PCM)", EventType.INFO)

        // Notify BlackBerry that audio streaming is starting
        service.sendPacket(BSBPacket("VOICE_START", listOf(SAMPLE_RATE.toString(), CHANNELS.toString(), BITS_PER_SAMPLE.toString())))

        val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isMicrophoneMute = false
        } catch (e: Exception) {
            // ignore
        }

        recordJob = scope.launch {
            var audioRecord: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
                val bufferSize = maxOf(minBuf, CHUNK_SIZE * 4)

                // Try audio sources in order of resilience during in-call phone state
                val sources = listOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.DEFAULT
                )

                for (source in sources) {
                    try {
                        val ar = AudioRecord(source, SAMPLE_RATE, CHANNEL_IN, ENCODING, bufferSize)
                        if (ar.state == AudioRecord.STATE_INITIALIZED) {
                            audioRecord = ar
                            Log.d(TAG, "AudioRecord initialized successfully with source: $source")
                            break
                        } else {
                            ar.release()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to initialize AudioRecord with source $source: ${e.message}")
                    }
                }

                if (audioRecord != null && audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording()
                    val pcmBuffer = ByteArray(CHUNK_SIZE)
                    val txCounter = AtomicInteger(0)

                    while (isStreaming.get() && isActive) {
                        // Blocking read: accurately self-paces at hardware audio sample rate (~20ms per CHUNK_SIZE)
                        val read = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
                        if (read > 0) {
                            val b64 = Base64.encodeToString(pcmBuffer, 0, read, Base64.NO_WRAP)
                            service.sendPacket(BSBPacket("VOICE_TX", listOf(b64)))
                            val count = txCounter.incrementAndGet()
                            if (count % 25 == 0) {
                                _txPackets.value = count
                            }
                        }
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
        _isBridgeActive.value = false
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

        // Notify BlackBerry that voice stream has finished
        BluetoothService.instance?.sendPacket(BSBPacket("VOICE_STOP", emptyList()))
        BridgeStateManager.logEvent("Passerelle Voix IP BlackBerry arrêtée", EventType.INFO)
    }

    /**
     * Plays voice packets received from BlackBerry microphone directly through Android voice communication stream
     */
    fun playIncomingVoice(context: Context, base64Data: String) {
        if (!_isVoipEnabled.value) return // Prevents echoing voice back on smartphone!
        try {
            if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                initAudioTrack(context)
            }
            val pcmBytes = Base64.decode(base64Data, Base64.NO_WRAP)
            audioTrack?.write(pcmBytes, 0, pcmBytes.size)
            _rxPackets.value = _rxPackets.value + 1
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
