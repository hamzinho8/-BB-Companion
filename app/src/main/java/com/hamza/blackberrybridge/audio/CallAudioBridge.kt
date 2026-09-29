package com.hamza.blackberrybridge.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
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
 * Universal Audio Bridge over Bluetooth RFCOMM / SPP.
 * Transforms BlackBerry Curve 9300 into a real wireless Bluetooth earphone / speaker
 * by capturing Android system audio (YouTube, Spotify, music, games, calls)
 * and streaming continuous 500ms self-contained WAV blocks (2 packets per second).
 */
object CallAudioBridge {
    private const val TAG = "CallAudioBridge"
    const val SAMPLE_RATE = 8000
    const val CHANNELS: Short = 1
    const val BITS_PER_SAMPLE: Short = 16
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    // Exact 500ms of 8000Hz 16-bit Mono:
    // 8000 samples/sec * 0.5s = 4000 samples * 2 bytes = 8000 bytes PCM raw audio
    const val PCM_CHUNK_SIZE = 8000

    private val isStreaming = AtomicBoolean(false)
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _txPackets = MutableStateFlow(0)
    val txPackets: StateFlow<Int> = _txPackets.asStateFlow()

    private val _rxPackets = MutableStateFlow(0)
    val rxPackets: StateFlow<Int> = _rxPackets.asStateFlow()

    private val _isVoipEnabled = MutableStateFlow(false)
    val isVoipEnabled: StateFlow<Boolean> = _isVoipEnabled.asStateFlow()

    private val _isBridgeActive = MutableStateFlow(false)
    val isBridgeActive: StateFlow<Boolean> = _isBridgeActive.asStateFlow()

    // Holds MediaProjection for Android 10+ internal digital audio capture
    var activeMediaProjection: MediaProjection? = null

    fun setVoipEnabled(enabled: Boolean) {
        _isVoipEnabled.value = enabled
        if (!enabled && isStreaming.get()) {
            stopStreaming()
        }
    }

    fun startStreaming(service: BluetoothService) {
        if (!BridgeStateManager.isConnected.value) {
            Log.d(TAG, "BlackBerry non connecté - diffusion audio ignorée")
            BridgeStateManager.logEvent("BlackBerry non connecté", EventType.WARNING)
            return
        }

        if (isStreaming.get()) return

        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission missing")
            BridgeStateManager.logEvent("Permission micro requise pour l'audio", EventType.WARNING)
            return
        }

        _isVoipEnabled.value = true
        isStreaming.set(true)
        _isBridgeActive.value = true
        _txPackets.value = 0
        _rxPackets.value = 0

        Log.d(TAG, "Démarrage diffusion audio Bluetooth vers BlackBerry...")
        BridgeStateManager.logEvent("Diffusion Audio BB active (WAV 500ms)", EventType.INFO)

        // 1. Signal de début exact : "AUDIO_START\n"
        service.sendPacket(BSBPacket("AUDIO_START", emptyList()))

        recordJob = scope.launch {
            var audioRecord: AudioRecord? = null
            try {
                // Try Android 10+ MediaPlaybackCapture first if MediaProjection is available
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && activeMediaProjection != null) {
                    try {
                        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(activeMediaProjection!!)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build()

                        val ar = AudioRecord.Builder()
                            .setAudioPlaybackCaptureConfig(captureConfig)
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(ENCODING)
                                    .setSampleRate(SAMPLE_RATE)
                                    .setChannelMask(CHANNEL_IN)
                                    .build()
                            )
                            .setBufferSizeInBytes(PCM_CHUNK_SIZE * 4)
                            .build()

                        if (ar.state == AudioRecord.STATE_INITIALIZED) {
                            audioRecord = ar
                            Log.d(TAG, "AudioPlaybackCapture (Internal Digital Audio) initialisé avec succès !")
                            BridgeStateManager.logEvent("Son interne YouTube/Médias connecté", EventType.SUCCESS)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "AudioPlaybackCapture init failed: ${e.message}")
                    }
                }

                // Fallback to hardware audio sources (MIC / VOICE)
                if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
                    val bufferSize = maxOf(minBuf, PCM_CHUNK_SIZE * 4)
                    val sources = listOf(
                        MediaRecorder.AudioSource.MIC,
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        MediaRecorder.AudioSource.DEFAULT
                    )

                    for (source in sources) {
                        try {
                            val ar = AudioRecord(source, SAMPLE_RATE, CHANNEL_IN, ENCODING, bufferSize)
                            if (ar.state == AudioRecord.STATE_INITIALIZED) {
                                audioRecord = ar
                                Log.d(TAG, "AudioRecord initialisé avec la source: $source")
                                break
                            } else {
                                ar.release()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Erreur source $source: ${e.message}")
                        }
                    }
                }

                if (audioRecord != null && audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording()
                    val pcmBuffer = ByteArray(PCM_CHUNK_SIZE)
                    val txCounter = AtomicInteger(0)

                    while (isStreaming.get() && isActive && BridgeStateManager.isConnected.value) {
                        // Blocking read of exactly 8000 bytes (500 ms at 8000Hz 16-bit Mono)
                        var bytesRead = 0
                        while (bytesRead < PCM_CHUNK_SIZE && isStreaming.get() && isActive && BridgeStateManager.isConnected.value) {
                            val r = audioRecord.read(pcmBuffer, bytesRead, PCM_CHUNK_SIZE - bytesRead)
                            if (r > 0) {
                                bytesRead += r
                            } else {
                                break
                            }
                        }

                        if (bytesRead == PCM_CHUNK_SIZE) {
                            // Génération de l'en-tête WAV 44 octets exact
                            val wavHeader = createWavHeader(PCM_CHUNK_SIZE, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE)
                            
                            // Assemblage du fichier WAV complet (44 + 8000 = 8044 octets)
                            val fullWav = ByteArray(44 + PCM_CHUNK_SIZE)
                            System.arraycopy(wavHeader, 0, fullWav, 0, 44)
                            System.arraycopy(pcmBuffer, 0, fullWav, 44, PCM_CHUNK_SIZE)

                            // Encodage Base64 strict NO_WRAP (sans aucun \n parasite)
                            val base64 = Base64.encodeToString(fullWav, Base64.NO_WRAP)

                            // 2. Envoi du paquet : "AUDIO_CHUNK|<base64_wav>\n"
                            service.sendPacket(BSBPacket("AUDIO_CHUNK", listOf(base64)))

                            val count = txCounter.incrementAndGet()
                            _txPackets.value = count
                        }
                    }
                } else {
                    Log.e(TAG, "Impossible d'initialiser AudioRecord")
                    BridgeStateManager.logEvent("Échec initialisation capture audio", EventType.ERROR)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur boucle audio Bluetooth", e)
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {}
            }
        }
    }

    fun stopStreaming() {
        if (!isStreaming.getAndSet(false)) return
        _isBridgeActive.value = false
        Log.d(TAG, "Arrêt diffusion audio Bluetooth vers BlackBerry...")
        recordJob?.cancel()
        recordJob = null

        // 3. Signal de fin exact : "AUDIO_STOP\n"
        BluetoothService.instance?.sendPacket(BSBPacket("AUDIO_STOP", emptyList()))
        BridgeStateManager.logEvent("Diffusion Audio BB arrêtée", EventType.INFO)
    }

    fun toggleStreaming(service: BluetoothService) {
        if (isStreaming.get()) {
            stopStreaming()
        } else {
            startStreaming(service)
        }
    }

    /**
     * Générateur d'en-tête standard RIFF/WAVE de 44 octets
     */
    fun createWavHeader(pcmDataLen: Int, sampleRate: Int = 8000, channels: Short = 1, bitsPerSample: Short = 16): ByteArray {
        val totalDataLen = pcmDataLen + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val header = ByteArray(44)

        // RIFF/WAVE header
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // Taille bloc fmt (16)
        header[20] = 1; header[21] = 0 // Format PCM = 1
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * (bitsPerSample / 8)).toByte(); header[33] = 0 // Block align
        header[34] = bitsPerSample.toByte(); header[35] = 0

        // data chunk
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmDataLen and 0xff).toByte()
        header[41] = ((pcmDataLen shr 8) and 0xff).toByte()
        header[42] = ((pcmDataLen shr 16) and 0xff).toByte()
        header[43] = ((pcmDataLen shr 24) and 0xff).toByte()
        return header
    }

    fun playIncomingVoice(context: Context, base64Data: String) {
        _rxPackets.value = _rxPackets.value + 1
    }
}
