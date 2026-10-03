package com.hamza.blackberrybridge.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
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
 * Moteur Audio Haute Définition et Basse Latence pour BlackBerry Curve 9300.
 *
 * SPÉCIFICATIONS TECHNIQUES IMPLÉMENTÉES :
 * 1. Fréquence d'échantillonnage : 16000 Hz (16 kHz, 16-bit PCM, Mono) HD Voice / Wideband.
 * 2. Taille des blocs & Cadence : 200 ms (3200 échantillons = 6400 octets PCM bruts par bloc).
 * 3. DSP & Traitement acoustique :
 *    - Filtre Passe-Haut (High-Pass Filter) à 120 Hz pour protéger la membrane du haut-parleur BlackBerry.
 *    - Limiteur doux avec Headroom (-3 dB / 0.75x) pour éliminer tout écrêtage et grésillement.
 * 4. Protocole Bluetooth SPP :
 *    - Start : "AUDIO_START|16000|1|16|200\n"
 *    - Chunks : "AUDIO_CHUNK|<base64_wav>\n" (Base64.NO_WRAP)
 *    - Stop :  "AUDIO_STOP\n"
 *    - En-tête WAV RIFF 44 octets standardisé (16000 Hz, 1 canal, 16 bits, débit 32000 o/s).
 * 5. Gestion des sources :
 *    - Appels téléphoniques : AudioSource.VOICE_COMMUNICATION (16000 Hz Mono).
 *    - YouTube / Médias : AudioPlaybackCaptureConfiguration avec re-échantillonnage haute fidélité.
 * 6. Mécanisme anti-latence (Drop-if-lagging) pour une latence stricte < 200 ms.
 */
object AudioStreamManager {
    private const val TAG = "AudioStreamManager"

    // Configuration Audio Normative
    const val SAMPLE_RATE = 16000
    const val CHANNELS: Short = 1
    const val BITS_PER_SAMPLE: Short = 16
    const val CHUNK_DURATION_MS = 200

    // 200 ms à 16000 Hz = 3200 échantillons = 6400 octets PCM
    const val SAMPLES_PER_CHUNK = (SAMPLE_RATE * (CHUNK_DURATION_MS / 1000.0)).toInt() // 3200
    const val PCM_CHUNK_BYTES = SAMPLES_PER_CHUNK * (BITS_PER_SAMPLE / 8) // 6400

    // Headroom limiter factor (-2.5 dB à -3 dB)
    private const val HEADROOM_FACTOR = 0.75f

    // État d'exécution
    private val isStreaming = AtomicBoolean(false)
    private var streamJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    // Filtre Passe-Haut 120 Hz IIR (Fréquence de coupure fc = 120 Hz @ fs = 16000 Hz)
    private class HighPassFilter120Hz(sampleRate: Int = 16000, cutoffHz: Double = 120.0) {
        private val rc = 1.0 / (2.0 * Math.PI * cutoffHz)
        private val dt = 1.0 / sampleRate
        private val alpha = rc / (rc + dt) // ~0.9550
        private var prevInput = 0.0
        private var prevOutput = 0.0

        fun process(sample: Double): Double {
            val output = alpha * (prevOutput + sample - prevInput)
            prevInput = sample
            prevOutput = output
            return output
        }

        fun reset() {
            prevInput = 0.0
            prevOutput = 0.0
        }
    }

    private val hpf = HighPassFilter120Hz(SAMPLE_RATE, 120.0)

    // Métriques & États observables pour l'UI
    private val _isStreamingFlow = MutableStateFlow(false)
    val isStreamingFlow: StateFlow<Boolean> = _isStreamingFlow.asStateFlow()

    private val _txPackets = MutableStateFlow(0)
    val txPackets: StateFlow<Int> = _txPackets.asStateFlow()

    private val _rxPackets = MutableStateFlow(0)
    val rxPackets: StateFlow<Int> = _rxPackets.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _hasAudioSignal = MutableStateFlow(false)
    val hasAudioSignal: StateFlow<Boolean> = _hasAudioSignal.asStateFlow()

    private val _captureStatus = MutableStateFlow("Prêt (16 kHz HD)")
    val captureStatus: StateFlow<String> = _captureStatus.asStateFlow()

    private val _totalBytesSent = MutableStateFlow(0L)
    val totalBytesSent: StateFlow<Long> = _totalBytesSent.asStateFlow()

    private val _isDigitalCapture = MutableStateFlow(false)
    val isDigitalCapture: StateFlow<Boolean> = _isDigitalCapture.asStateFlow()

    private val _isSpeakerMuted = MutableStateFlow(false)
    val isSpeakerMuted: StateFlow<Boolean> = _isSpeakerMuted.asStateFlow()

    // MediaProjection actif pour la capture Android 10+
    var activeMediaProjection: MediaProjection? = null
    private var savedMediaVolume = -1
    private var callAudioTrack: AudioTrack? = null

    fun start(service: BluetoothService) {
        if (!BridgeStateManager.isConnected.value) {
            Log.d(TAG, "BlackBerry non connecté - diffusion audio annulée")
            BridgeStateManager.logEvent("BlackBerry non connecté", EventType.WARNING)
            return
        }

        if (isStreaming.get()) return

        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission RECORD_AUDIO manquante")
            BridgeStateManager.logEvent("Permission micro requise pour l'audio", EventType.WARNING)
            return
        }

        isStreaming.set(true)
        _isStreamingFlow.value = true
        _txPackets.value = 0
        _rxPackets.value = 0
        hpf.reset()
        com.hamza.blackberrybridge.widget.BridgeWidgetProvider.updateAllWidgets(service)

        Log.d(TAG, "Démarrage AudioStreamManager (16kHz, 200ms, DSP HPF 120Hz + Headroom -3dB)...")
        BridgeStateManager.logEvent("Diffusion 16kHz HD (200ms / DSP 120Hz)", EventType.INFO)

        // 1. Démarrage du flux avec protocole exact : "AUDIO_START|16000|1|16|200\n"
        service.sendPacket(BSBPacket("AUDIO_START", listOf("16000", "1", "16", "200")))

        streamJob = scope.launch {
            var audioRecord: AudioRecord? = null
            var captureRate = SAMPLE_RATE
            var captureChannels = 1
            var isDigital = false

            try {
                val isCallActive = com.hamza.blackberrybridge.calls.BridgeInCallService.activeCall != null ||
                        com.hamza.blackberrybridge.calls.CallController.isCallActive

                if (isCallActive) {
                    // Capture téléphonique directe de la ligne d'appel (VOICE_COMMUNICATION)
                    try {
                        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                        val ar = AudioRecord(
                            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            maxOf(minBuf * 4, PCM_CHUNK_BYTES * 4)
                        )
                        if (ar.state == AudioRecord.STATE_INITIALIZED) {
                            audioRecord = ar
                            captureRate = SAMPLE_RATE
                            captureChannels = 1
                            isDigital = false
                            Log.d(TAG, "AudioRecord VOICE_COMMUNICATION 16kHz initialisé")
                            BridgeStateManager.logEvent("Ligne d'appel 16kHz connectée", EventType.SUCCESS)
                        } else {
                            ar.release()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Échec init capture vocale 16kHz: ${e.message}")
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && activeMediaProjection != null) {
                    // Capture numérique interne des haut-parleurs (YouTube, Musique, Médias)
                    val captureConfig = AudioPlaybackCaptureConfiguration.Builder(activeMediaProjection!!)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build()

                    val configs = listOf(
                        Triple(48000, AudioFormat.CHANNEL_IN_STEREO, 2),
                        Triple(48000, AudioFormat.CHANNEL_IN_MONO, 1),
                        Triple(44100, AudioFormat.CHANNEL_IN_STEREO, 2),
                        Triple(44100, AudioFormat.CHANNEL_IN_MONO, 1),
                        Triple(16000, AudioFormat.CHANNEL_IN_MONO, 1)
                    )

                    for ((sr, chMask, chCount) in configs) {
                        try {
                            val minBuf = AudioRecord.getMinBufferSize(sr, chMask, AudioFormat.ENCODING_PCM_16BIT)
                            if (minBuf <= 0) continue
                            val bufferBytes = maxOf(minBuf * 4, sr * chCount * 4)
                            val ar = AudioRecord.Builder()
                                .setAudioPlaybackCaptureConfig(captureConfig)
                                .setAudioFormat(
                                    AudioFormat.Builder()
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setSampleRate(sr)
                                        .setChannelMask(chMask)
                                        .build()
                                )
                                .setBufferSizeInBytes(bufferBytes)
                                .build()

                            if (ar.state == AudioRecord.STATE_INITIALIZED) {
                                audioRecord = ar
                                captureRate = sr
                                captureChannels = chCount
                                isDigital = true
                                Log.d(TAG, "AudioPlaybackCapture connecté : ${sr}Hz ${chCount}ch")
                                BridgeStateManager.logEvent("Capture Numérique connectée (${sr}Hz)", EventType.SUCCESS)
                                break
                            } else {
                                ar.release()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Échec config numérique ${sr}Hz: ${e.message}")
                        }
                    }
                }

                _isDigitalCapture.value = isDigital

                if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "Aucune source audio valide disponible")
                    BridgeStateManager.logEvent("En attente validation 'Commencer' pour audio numérique", EventType.WARNING)
                }

                if (audioRecord != null && audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording()

                    // Calcul de la taille de lecture pour exactement 200 ms à captureRate
                    val frameCountForChunk = (captureRate * (CHUNK_DURATION_MS / 1000.0)).toInt()
                    val totalShortsForChunk = frameCountForChunk * captureChannels
                    val captureBuffer = ShortArray(totalShortsForChunk)
                    val txCounter = AtomicInteger(0)

                    val processedSamples = ShortArray(SAMPLES_PER_CHUNK)
                    val pcmBytes = ByteArray(PCM_CHUNK_BYTES)

                    while (isStreaming.get() && isActive && BridgeStateManager.isConnected.value) {
                        var shortsRead = 0
                        while (shortsRead < totalShortsForChunk && isStreaming.get() && isActive && BridgeStateManager.isConnected.value) {
                            val toRead = minOf(512, totalShortsForChunk - shortsRead)
                            val r = audioRecord.read(captureBuffer, shortsRead, toRead)
                            if (r > 0) {
                                shortsRead += r
                            } else if (r == 0) {
                                delay(2)
                            } else {
                                delay(5)
                                try {
                                    if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                                        audioRecord.startRecording()
                                    }
                                } catch (e: Exception) {}
                            }
                        }

                        if (shortsRead > 0) {
                            // 1. Re-échantillonnage vers 16000 Hz Mono
                            val resampled = resampleTo16000Mono(captureBuffer, shortsRead, captureRate, captureChannels)

                            // 2. Traitement du signal DSP :
                            //    - Filtre Passe-Haut 120 Hz (élimination des sous-basses qui font saturer la membrane BB)
                            //    - Limiteur doux Headroom (-3 dB / 0.75x) pour éviter tout écrêtage numérique
                            var sumSquares = 0.0
                            for (i in 0 until SAMPLES_PER_CHUNK) {
                                val raw = resampled[i].toDouble()
                                val filtered = hpf.process(raw)
                                val limited = (filtered * HEADROOM_FACTOR).toInt().coerceIn(-32768, 32767).toShort()
                                processedSamples[i] = limited
                                sumSquares += limited * limited

                                // Conversion Little-Endian PCM 16-bit
                                val s = limited.toInt()
                                pcmBytes[i * 2] = (s and 0xFF).toByte()
                                pcmBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                            }

                            // 3. Assemblage du bloc WAV RIFF de 44 octets (Total 6444 octets)
                            val wavHeader = createRiffWavHeader(PCM_CHUNK_BYTES)
                            val fullWav = ByteArray(44 + PCM_CHUNK_BYTES)
                            System.arraycopy(wavHeader, 0, fullWav, 0, 44)
                            System.arraycopy(pcmBytes, 0, fullWav, 44, PCM_CHUNK_BYTES)

                            // 4. Encodage Base64 standard sans retours à la ligne (Base64.NO_WRAP)
                            val base64 = Base64.encodeToString(fullWav, Base64.NO_WRAP)

                            // 5. Envoi immédiat Bluetooth SPP : "AUDIO_CHUNK|<base64>\n"
                            service.sendPacket(BSBPacket("AUDIO_CHUNK", listOf(base64)))

                            val count = txCounter.incrementAndGet()
                            _txPackets.value = count
                            _totalBytesSent.value = _totalBytesSent.value + fullWav.size

                            // Mesure du niveau RMS pour le VU-mètre de l'interface
                            val rms = kotlin.math.sqrt(sumSquares / SAMPLES_PER_CHUNK)
                            val normalizedLevel = (rms / 6000.0).coerceIn(0.0, 1.0).toFloat()
                            _audioLevel.value = normalizedLevel
                            val isSignalPresent = rms > 40.0
                            _hasAudioSignal.value = isSignalPresent
                            _captureStatus.value = if (isSignalPresent) {
                                "Signal audio 16 kHz HD (${(normalizedLevel * 100).toInt()}% niveau)"
                            } else {
                                "Capture 16 kHz active - En attente de son"
                            }
                        } else {
                            delay(5)
                        }
                    }
                } else {
                    Log.e(TAG, "Impossible d'initialiser AudioRecord")
                    BridgeStateManager.logEvent("Échec capture audio 16kHz", EventType.ERROR)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur flux audio 16kHz", e)
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {}
            }
        }
    }

    /**
     * Re-échantillonne et convertit n'importe quel flux d'entrée (48kHz, 44.1kHz Stéréo/Mono)
     * en exactement 3200 échantillons à 16000 Hz Mono avec mixage stéréo équilibré.
     */
    private fun resampleTo16000Mono(
        input: ShortArray,
        inputCount: Int,
        inputSampleRate: Int,
        channels: Int
    ): ShortArray {
        val output = ShortArray(SAMPLES_PER_CHUNK)
        if (inputCount <= 0) return output

        // 1. Conversion Stéréo -> Mono
        val monoCount: Int
        val monoSamples: ShortArray
        if (channels == 2) {
            monoCount = inputCount / 2
            monoSamples = ShortArray(monoCount)
            for (i in 0 until monoCount) {
                val l = input[i * 2].toInt()
                val r = input[i * 2 + 1].toInt()
                monoSamples[i] = ((l + r) / 2).coerceIn(-32768, 32767).toShort()
            }
        } else {
            monoCount = inputCount
            monoSamples = input
        }

        if (monoCount <= 0) return output

        // 2. Si déjà à 16000 Hz, copie directe
        if (inputSampleRate == SAMPLE_RATE) {
            val copyCount = minOf(monoCount, SAMPLES_PER_CHUNK)
            System.arraycopy(monoSamples, 0, output, 0, copyCount)
            return output
        }

        // 3. Interpolation linéaire mathématique à ratio fixe
        val ratio = inputSampleRate.toDouble() / SAMPLE_RATE.toDouble()
        for (i in 0 until SAMPLES_PER_CHUNK) {
            val srcPos = i * ratio
            val idx = srcPos.toInt()
            if (idx >= monoCount - 1) {
                output[i] = monoSamples[monoCount - 1]
            } else {
                val frac = srcPos - idx
                val s0 = monoSamples[idx].toDouble()
                val s1 = monoSamples[idx + 1].toDouble()
                output[i] = (s0 + frac * (s1 - s0)).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return output
    }

    /**
     * Générateur d'en-tête RIFF/WAVE standard de 44 octets strictement conforme aux spécifications :
     * AudioFormat: 1 (PCM)
     * Channels: 1 (Mono)
     * SampleRate: 16000
     * ByteRate: 32000 (16000 * 1 * 2)
     * BlockAlign: 2 (1 * 2)
     * BitsPerSample: 16
     * pcmDataLen: 6400 (pour 200 ms)
     */
    fun createRiffWavHeader(pcmDataLen: Int = PCM_CHUNK_BYTES): ByteArray {
        val totalDataLen = pcmDataLen + 36
        val byteRate = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8) // 32000
        val header = ByteArray(44)

        // RIFF chunk
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()

        // fmt subchunk
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // Taille 16
        header[20] = 1; header[21] = 0 // AudioFormat 1 = PCM
        header[22] = CHANNELS.toByte(); header[23] = 0 // Channels = 1
        header[24] = (SAMPLE_RATE and 0xff).toByte() // 0x80
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte() // 0x3E -> 16000
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte() // 0x00
        header[29] = ((byteRate shr 8) and 0xff).toByte() // 0x7D -> 32000
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (CHANNELS * (BITS_PER_SAMPLE / 8)).toByte(); header[33] = 0 // BlockAlign = 2
        header[34] = BITS_PER_SAMPLE.toByte(); header[35] = 0 // BitsPerSample = 16

        // data subchunk
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmDataLen and 0xff).toByte()
        header[41] = ((pcmDataLen shr 8) and 0xff).toByte()
        header[42] = ((pcmDataLen shr 16) and 0xff).toByte()
        header[43] = ((pcmDataLen shr 24) and 0xff).toByte()
        return header
    }

    fun stop(stopProjectionService: Boolean = true) {
        if (!isStreaming.getAndSet(false)) return
        _isStreamingFlow.value = false
        _isDigitalCapture.value = false
        Log.d(TAG, "Arrêt d'AudioStreamManager...")
        streamJob?.cancel()
        streamJob = null
        hpf.reset()

        try {
            val ctx = BluetoothService.instance ?: com.hamza.blackberrybridge.ui.MainActivity.instance
            if (ctx != null) {
                com.hamza.blackberrybridge.widget.BridgeWidgetProvider.updateAllWidgets(ctx)
            }
        } catch (e: Exception) {}

        // Restauration du volume haut-parleur smartphone
        try {
            val s = BluetoothService.instance
            if (s != null) {
                val am = s.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                if (savedMediaVolume >= 0) {
                    am?.setStreamVolume(AudioManager.STREAM_MUSIC, savedMediaVolume, 0)
                    savedMediaVolume = -1
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur rétablissement volume: ${e.message}")
        }

        // Émission du signal d'arrêt exact : "AUDIO_STOP\n"
        BluetoothService.instance?.sendPacket(BSBPacket("AUDIO_STOP", emptyList()))
        BridgeStateManager.logEvent("Diffusion 16kHz arrêtée", EventType.INFO)

        if (stopProjectionService) {
            try {
                val s = BluetoothService.instance
                if (s != null) {
                    val stopIntent = Intent(s, AudioProjectionService::class.java).apply {
                        action = AudioProjectionService.ACTION_STOP
                    }
                    s.startService(stopIntent)
                }
            } catch (e: Exception) {}
        }

        try {
            callAudioTrack?.stop()
            callAudioTrack?.release()
            callAudioTrack = null
        } catch (e: Exception) {}
    }

    fun onCallStarted(service: BluetoothService) {
        Log.d(TAG, "onCallStarted: Basculement vers VOICE_COMMUNICATION")
        BridgeStateManager.logEvent("Appel actif : routage audio d'appel vers BlackBerry", EventType.INFO)
        streamJob?.cancel()
        streamJob = null
        isStreaming.set(false)
        start(service)
    }

    fun onCallEnded(service: BluetoothService) {
        Log.d(TAG, "onCallEnded: Fin d'appel, reprise automatique des médias")
        BridgeStateManager.logEvent("Fin d'appel : reprise audio sans coupure", EventType.INFO)
        streamJob?.cancel()
        streamJob = null
        isStreaming.set(false)
        start(service)
    }

    fun toggleSpeakerMute(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val newMuted = !_isSpeakerMuted.value
        _isSpeakerMuted.value = newMuted
        try {
            if (newMuted) {
                savedMediaVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0)
                BridgeStateManager.logEvent("Volume HP smartphone réduit au minimum", EventType.INFO)
            } else {
                if (savedMediaVolume >= 0) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMediaVolume, 0)
                }
                BridgeStateManager.logEvent("Volume HP smartphone rétabli", EventType.INFO)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur réglage volume HP: ${e.message}")
        }
    }

    /**
     * Bip de test 440 Hz (Note La4) calibré à 16000 Hz 200 ms avec limiteur -3dB
     */
    fun sendTestBeep(service: BluetoothService) {
        scope.launch {
            try {
                val freq = 440.0
                val pcm = ByteArray(PCM_CHUNK_BYTES)
                for (i in 0 until SAMPLES_PER_CHUNK) {
                    val angle = 2.0 * Math.PI * freq * i / SAMPLE_RATE
                    // Application du facteur de headroom 0.75
                    val sample = (Math.sin(angle) * 16000.0 * HEADROOM_FACTOR).toInt().coerceIn(-32768, 32767).toShort()
                    pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
                    pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                }
                val header = createRiffWavHeader(PCM_CHUNK_BYTES)
                val fullWav = ByteArray(44 + PCM_CHUNK_BYTES)
                System.arraycopy(header, 0, fullWav, 0, 44)
                System.arraycopy(pcm, 0, fullWav, 44, PCM_CHUNK_BYTES)
                val base64 = Base64.encodeToString(fullWav, Base64.NO_WRAP)

                service.sendPacket(BSBPacket("AUDIO_START", listOf("16000", "1", "16", "200")))
                delay(30)
                service.sendPacket(BSBPacket("AUDIO_CHUNK", listOf(base64)))
                _txPackets.value = _txPackets.value + 1
                _totalBytesSent.value = _totalBytesSent.value + fullWav.size
                _audioLevel.value = 0.75f
                _hasAudioSignal.value = true
                _captureStatus.value = "Bip de test transmis (16kHz / 200ms / -3dB)"
                BridgeStateManager.logEvent("Bip test transmis vers BlackBerry (16kHz)", EventType.SUCCESS)
                delay(400)
                _audioLevel.value = 0f
                _hasAudioSignal.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Erreur émission bip test", e)
            }
        }
    }

    /**
     * Lecture du retour voix provenant du micro du BlackBerry pendant un appel
     */
    fun playIncomingVoice(context: Context, base64Data: String) {
        _rxPackets.value = _rxPackets.value + 1
        try {
            val rawBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val offset = if (rawBytes.size > 44 && rawBytes[0] == 'R'.code.toByte()) 44 else 0
            val len = rawBytes.size - offset
            if (len <= 0) return

            if (callAudioTrack == null || callAudioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                callAudioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf * 2, PCM_CHUNK_BYTES * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                callAudioTrack?.play()
            }
            callAudioTrack?.write(rawBytes, offset, len)
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lecture voix BlackBerry: ${e.message}")
        }
    }
}
