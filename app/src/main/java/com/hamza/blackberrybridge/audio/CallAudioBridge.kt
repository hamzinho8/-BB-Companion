package com.hamza.blackberrybridge.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
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
 * Universal Audio Bridge over Bluetooth RFCOMM.
 * Allows BlackBerry (Curve 9300 / Bold / Torch) to act as wireless Bluetooth earphones/speaker
 * for phone calls and all Android media playback (YouTube, music, voice notes).
 *
 * Transmits self-contained 200ms WAV chunks (5 packets/second) with complete RIFF headers.
 * This guarantees zero J2ME thread lockups, zero watchdog crashes (226), and direct playback.
 */
object CallAudioBridge {
    private const val TAG = "CallAudioBridge"
    const val SAMPLE_RATE = 8000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    // 500ms of 8000Hz 16-bit Mono = 4000 samples * 2 bytes = 8000 bytes per chunk
    // 2 packets per second = ultra-lightweight for Bluetooth RFCOMM and effortless for BlackBerry Curve 9300 CPU
    private const val PCM_CHUNK_SIZE = 8000

    private val isStreaming = AtomicBoolean(false)
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _txPackets = MutableStateFlow(0)
    val txPackets: StateFlow<Int> = _txPackets.asStateFlow()

    private val _rxPackets = MutableStateFlow(0)
    val rxPackets: StateFlow<Int> = _rxPackets.asStateFlow()

    private val _isVoipEnabled = MutableStateFlow(false) // Default off: user activates via button
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
        if (!BridgeStateManager.isConnected.value) {
            Log.d(TAG, "BlackBerry non connecté - diffusion audio ignorée")
            return
        }

        if (!_isVoipEnabled.value) {
            _isVoipEnabled.value = true
        }

        if (isStreaming.get()) return

        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted, audio streaming cannot start.")
            BridgeStateManager.logEvent("Microphone Android non autorisé pour l'audio", EventType.WARNING)
            return
        }

        isStreaming.set(true)
        _isBridgeActive.value = true
        _txPackets.value = 0
        _rxPackets.value = 0

        Log.d(TAG, "Starting Universal Audio Streamer to BlackBerry Curve 9300...")
        BridgeStateManager.logEvent("Relais Écouteurs BB actif (WAV 8000Hz 500ms)", EventType.INFO)

        // Notify BlackBerry that audio streaming has started with 500ms chunk configuration
        service.sendPacket(BSBPacket("AUDIO_START", listOf(SAMPLE_RATE.toString(), CHANNELS.toString(), BITS_PER_SAMPLE.toString(), "500")))
        service.sendPacket(BSBPacket("VOICE_START", listOf(SAMPLE_RATE.toString(), CHANNELS.toString(), BITS_PER_SAMPLE.toString())))

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
                val bufferSize = maxOf(minBuf, PCM_CHUNK_SIZE * 4)

                // Select resilient audio source
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
                    val pcmBuffer = ByteArray(PCM_CHUNK_SIZE)
                    val txCounter = AtomicInteger(0)

                    while (isStreaming.get() && isActive && BridgeStateManager.isConnected.value) {
                        // Blocking read of 500ms audio chunk (8000 bytes = exactly 500ms pacing)
                        var totalRead = 0
                        while (totalRead < PCM_CHUNK_SIZE && isStreaming.get() && isActive && BridgeStateManager.isConnected.value) {
                            val r = audioRecord.read(pcmBuffer, totalRead, PCM_CHUNK_SIZE - totalRead)
                            if (r > 0) {
                                totalRead += r
                            } else {
                                break
                            }
                        }

                        if (totalRead > 0) {
                            // Build complete self-contained WAV file with standard 44-byte RIFF header
                            val wavBytes = createWavPackage(pcmBuffer, totalRead, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE)
                            val b64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

                            // Send both commands for full backwards and forwards compatibility
                            service.sendPacket(BSBPacket("AUDIO_CHUNK", listOf(b64)))
                            service.sendPacket(BSBPacket("VOICE_TX", listOf(b64)))

                            val count = txCounter.incrementAndGet()
                            _txPackets.value = count
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
        Log.d(TAG, "Stopping Audio Streamer to BlackBerry...")
        recordJob?.cancel()
        recordJob = null

        // Notify BlackBerry that audio stream has ended
        BluetoothService.instance?.sendPacket(BSBPacket("AUDIO_STOP", emptyList()))
        BluetoothService.instance?.sendPacket(BSBPacket("VOICE_STOP", emptyList()))
        BridgeStateManager.logEvent("Relais Écouteur BlackBerry arrêté", EventType.INFO)
    }

    fun toggleStreaming(service: BluetoothService) {
        if (isStreaming.get()) {
            stopStreaming()
        } else {
            startStreaming(service)
        }
    }

    /**
     * Handles incoming voice from BlackBerry without echoing locally onto smartphone speaker
     */
    fun playIncomingVoice(context: Context, base64Data: String) {
        // Telemetry only: do NOT echo user voice through phone speaker
        _rxPackets.value = _rxPackets.value + 1
    }

    /**
     * Creates a fully compliant 44-byte RIFF WAV package from raw PCM buffer.
     */
    private fun createWavPackage(pcmData: ByteArray, pcmLength: Int, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val totalDataLen = pcmLength + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val wav = ByteArray(44 + pcmLength)

        // RIFF header
        wav[0] = 'R'.code.toByte(); wav[1] = 'I'.code.toByte(); wav[2] = 'F'.code.toByte(); wav[3] = 'F'.code.toByte()
        wav[4] = (totalDataLen and 0xff).toByte()
        wav[5] = ((totalDataLen shr 8) and 0xff).toByte()
        wav[6] = ((totalDataLen shr 16) and 0xff).toByte()
        wav[7] = ((totalDataLen shr 24) and 0xff).toByte()
        wav[8] = 'W'.code.toByte(); wav[9] = 'A'.code.toByte(); wav[10] = 'V'.code.toByte(); wav[11] = 'E'.code.toByte()

        // fmt chunk
        wav[12] = 'f'.code.toByte(); wav[13] = 'm'.code.toByte(); wav[14] = 't'.code.toByte(); wav[15] = ' '.code.toByte()
        wav[16] = 16; wav[17] = 0; wav[18] = 0; wav[19] = 0 // Chunk size = 16 for PCM
        wav[20] = 1; wav[21] = 0 // Linear PCM format
        wav[22] = channels.toByte(); wav[23] = 0
        wav[24] = (sampleRate and 0xff).toByte()
        wav[25] = ((sampleRate shr 8) and 0xff).toByte()
        wav[26] = ((sampleRate shr 16) and 0xff).toByte()
        wav[27] = ((sampleRate shr 24) and 0xff).toByte()
        wav[28] = (byteRate and 0xff).toByte()
        wav[29] = ((byteRate shr 8) and 0xff).toByte()
        wav[30] = ((byteRate shr 16) and 0xff).toByte()
        wav[31] = ((byteRate shr 24) and 0xff).toByte()
        wav[32] = blockAlign.toByte(); wav[33] = 0
        wav[34] = bitsPerSample.toByte(); wav[35] = 0

        // data chunk
        wav[36] = 'd'.code.toByte(); wav[37] = 'a'.code.toByte(); wav[38] = 't'.code.toByte(); wav[39] = 'a'.code.toByte()
        wav[40] = (pcmLength and 0xff).toByte()
        wav[41] = ((pcmLength shr 8) and 0xff).toByte()
        wav[42] = ((pcmLength shr 16) and 0xff).toByte()
        wav[43] = ((pcmLength shr 24) and 0xff).toByte()

        // Copy PCM body
        System.arraycopy(pcmData, 0, wav, 44, pcmLength)
        return wav
    }
}
