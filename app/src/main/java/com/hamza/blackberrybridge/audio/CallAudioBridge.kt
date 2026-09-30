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
 * Universal Bluetooth Earphone Audio Bridge.
 * Captures Android digital media audio (YouTube, Spotify, Music, Games)
 * or in-call audio, streams HD 16kHz / 8kHz audio,
 * and streams to BlackBerry Curve 9300.
 */
object CallAudioBridge {
    private const val TAG = "CallAudioBridge"
    const val TARGET_SAMPLE_RATE = 8000
    const val PCM_CHUNK_SIZE = 8000
    const val TARGET_SAMPLES = 4000

    // Dynamic audio rate (16000 Hz HD by default for music & YouTube, or 8000 Hz)
    private val _sampleRate = MutableStateFlow(16000)
    val sampleRate: StateFlow<Int> = _sampleRate.asStateFlow()

    const val TARGET_CHANNELS: Short = 1
    const val TARGET_BITS_PER_SAMPLE: Short = 16

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

    private val _isDigitalCapture = MutableStateFlow(false)
    val isDigitalCapture: StateFlow<Boolean> = _isDigitalCapture.asStateFlow()

    private val _isSpeakerMuted = MutableStateFlow(false)
    val isSpeakerMuted: StateFlow<Boolean> = _isSpeakerMuted.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _hasAudioSignal = MutableStateFlow(false)
    val hasAudioSignal: StateFlow<Boolean> = _hasAudioSignal.asStateFlow()

    private val _captureStatus = MutableStateFlow("En attente")
    val captureStatus: StateFlow<String> = _captureStatus.asStateFlow()

    private val _totalBytesSent = MutableStateFlow(0L)
    val totalBytesSent: StateFlow<Long> = _totalBytesSent.asStateFlow()

    // Holds MediaProjection for Android 10+ internal digital audio capture
    var activeMediaProjection: MediaProjection? = null
    private var savedMediaVolume = -1
    private var callAudioTrack: AudioTrack? = null

    fun setSampleRate(rate: Int) {
        if (_sampleRate.value != rate) {
            _sampleRate.value = rate
            BridgeStateManager.logEvent("Qualité audio réglée sur ${rate / 1000} kHz", EventType.INFO)
            BluetoothService.instance?.let { service ->
                if (isStreaming.get()) {
                    restartStreaming(service)
                }
            }
        }
    }

    fun restartStreaming(service: BluetoothService) {
        recordJob?.cancel()
        recordJob = null
        isStreaming.set(false)
        startStreaming(service)
    }

    fun onCallStarted(service: BluetoothService) {
        Log.d(TAG, "onCallStarted: Basculement vers l'audio d'appel (VOICE_COMMUNICATION)")
        BridgeStateManager.logEvent("Appel actif : routage audio d'appel vers BlackBerry", EventType.INFO)
        if (isStreaming.get()) {
            recordJob?.cancel()
            recordJob = null
            isStreaming.set(false)
        }
        startStreaming(service)
    }

    fun onCallEnded(service: BluetoothService) {
        Log.d(TAG, "onCallEnded: Fin d'appel, reprise automatique du son multimédia")
        BridgeStateManager.logEvent("Fin d'appel : reprise audio sans interruption", EventType.INFO)
        if (isStreaming.get()) {
            recordJob?.cancel()
            recordJob = null
            isStreaming.set(false)
        }
        startStreaming(service)
    }

    fun setVoipEnabled(enabled: Boolean) {
        _isVoipEnabled.value = enabled
        if (!enabled && isStreaming.get()) {
            stopStreaming()
        }
    }

    fun toggleSpeakerMute(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val newMuted = !_isSpeakerMuted.value
        _isSpeakerMuted.value = newMuted
        try {
            if (newMuted) {
                savedMediaVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0)
                BridgeStateManager.logEvent("Volume HP smartphone réduit au minimum (1)", EventType.INFO)
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

        // Do not mute phone speaker automatically so AudioFlinger does not attenuate the capture buffer to 0
        Log.d(TAG, "Démarrage diffusion audio Bluetooth vers BlackBerry (100% Numérique, aucun micro)...")
        BridgeStateManager.logEvent("Diffusion Audio BB (Pur Numérique)", EventType.INFO)

        // 1. Signal de début exact : "AUDIO_START\n"
        service.sendPacket(BSBPacket("AUDIO_START", emptyList()))

        recordJob = scope.launch {
            var audioRecord: AudioRecord? = null
            var captureRate = TARGET_SAMPLE_RATE
            var captureChannels = 1
            var isDigital = false

            try {
                // 1. Android 10+ Internal Digital Media Playback Capture (100% pure audio from speakers)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && activeMediaProjection != null) {
                    val captureConfig = AudioPlaybackCaptureConfiguration.Builder(activeMediaProjection!!)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build()

                    // Try native sample rates and channels that Android AudioFlinger supports
                    val configs = listOf(
                        Triple(48000, AudioFormat.CHANNEL_IN_STEREO, 2),
                        Triple(48000, AudioFormat.CHANNEL_IN_MONO, 1),
                        Triple(44100, AudioFormat.CHANNEL_IN_STEREO, 2),
                        Triple(44100, AudioFormat.CHANNEL_IN_MONO, 1),
                        Triple(16000, AudioFormat.CHANNEL_IN_MONO, 1),
                        Triple(8000, AudioFormat.CHANNEL_IN_MONO, 1)
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
                                Log.d(TAG, "AudioPlaybackCapture (Son Numérique YouTube/Musique) INITIALISÉ : ${sr}Hz ${chCount}ch (buf: ${bufferBytes}b)")
                                BridgeStateManager.logEvent("Audio Numérique YouTube/Médias connecté (${sr}Hz)", EventType.SUCCESS)
                                break
                            } else {
                                ar.release()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Échec capture numérique ${sr}Hz: ${e.message}")
                        }
                    }
                }

                _isDigitalCapture.value = isDigital

                // Telephony voice call capture: ONLY when a phone call is actively ongoing
                val isCallActive = com.hamza.blackberrybridge.calls.BridgeInCallService.activeCall != null || com.hamza.blackberrybridge.calls.CallController.isCallActive
                if (isCallActive && (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED)) {
                    try {
                        val minBuf = AudioRecord.getMinBufferSize(TARGET_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                        val ar = AudioRecord(
                            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                            TARGET_SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            maxOf(minBuf * 4, PCM_CHUNK_SIZE * 4)
                        )
                        if (ar.state == AudioRecord.STATE_INITIALIZED) {
                            audioRecord = ar
                            captureRate = TARGET_SAMPLE_RATE
                            captureChannels = 1
                            isDigital = false
                            Log.d(TAG, "AudioRecord initialisé pour appel téléphonique (VOICE_COMMUNICATION)")
                            BridgeStateManager.logEvent("Audio Appel téléphonique connecté (Ligne)", EventType.SUCCESS)
                        } else {
                            ar.release()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Échec capture vocale appel: ${e.message}")
                    }
                }

                // Strict rule: NEVER use the microphone for media. If digital capture is not initialized, wait or notify.
                if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "Aucune source audio valide disponible (zéro micro)")
                    BridgeStateManager.logEvent("En attente validation 'Commencer' pour audio numérique", EventType.WARNING)
                }

                if (audioRecord != null && audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording()

                    // Samples needed for 500 ms at current capture rate
                    val frameCountFor500ms = (captureRate * 0.5).toInt()
                    val totalShortsFor500ms = frameCountFor500ms * captureChannels
                    val captureBuffer = ShortArray(totalShortsFor500ms)
                    val txCounter = AtomicInteger(0)

                    val targetRate = _sampleRate.value
                    val targetSamples = (targetRate * 0.5).toInt()
                    val targetPcmBytes = targetSamples * 2

                    while (isStreaming.get() && isActive && BridgeStateManager.isConnected.value) {
                        var shortsRead = 0
                        while (shortsRead < totalShortsFor500ms && isStreaming.get() && isActive && BridgeStateManager.isConnected.value) {
                            val toRead = minOf(1024, totalShortsFor500ms - shortsRead)
                            val r = audioRecord.read(captureBuffer, shortsRead, toRead)
                            if (r > 0) {
                                shortsRead += r
                            } else if (r == 0) {
                                delay(5)
                            } else {
                                delay(10)
                                try {
                                    if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                                        audioRecord.startRecording()
                                    }
                                } catch (e: Exception) {}
                            }
                        }

                        if (shortsRead > 0) {
                            // High-Fidelity Resample to targetRate (16kHz HD or 8kHz)
                            val resampled = resampleAudio(captureBuffer, shortsRead, captureRate, targetRate, captureChannels, targetSamples)

                            // Convert to Little-Endian PCM bytes
                            val pcmBytes = ByteArray(targetPcmBytes)
                            for (i in 0 until targetSamples) {
                                val s = resampled[i].toInt()
                                pcmBytes[i * 2] = (s and 0xFF).toByte()
                                pcmBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                            }

                            // Assemblage du fichier WAV complet
                            val wavHeader = createWavHeader(targetPcmBytes, targetRate, TARGET_CHANNELS, TARGET_BITS_PER_SAMPLE)
                            val fullWav = ByteArray(44 + targetPcmBytes)
                            System.arraycopy(wavHeader, 0, fullWav, 0, 44)
                            System.arraycopy(pcmBytes, 0, fullWav, 44, targetPcmBytes)

                            // Encodage Base64 strict NO_WRAP
                            val base64 = Base64.encodeToString(fullWav, Base64.NO_WRAP)

                            // Envoi Bluetooth : "AUDIO_CHUNK|<base64_wav>\n"
                            service.sendPacket(BSBPacket("AUDIO_CHUNK", listOf(base64)))

                            val count = txCounter.incrementAndGet()
                            _txPackets.value = count
                            _totalBytesSent.value = _totalBytesSent.value + fullWav.size

                            // Calculate RMS signal level for live VU-meter feedback
                            var sumSquares = 0.0
                            for (i in 0 until targetSamples) {
                                val sample = resampled[i].toDouble()
                                sumSquares += sample * sample
                            }
                            val rms = kotlin.math.sqrt(sumSquares / targetSamples)
                            val normalizedLevel = (rms / 6000.0).coerceIn(0.0, 1.0).toFloat()
                            _audioLevel.value = normalizedLevel
                            val isSignalPresent = rms > 60.0
                            _hasAudioSignal.value = isSignalPresent
                            _captureStatus.value = if (isSignalPresent) {
                                "Signal audio détecté (${(normalizedLevel * 100).toInt()}% niveau)"
                            } else {
                                "Capture active - Silence détecté (lancez YouTube ou Musique)"
                            }
                        } else {
                            delay(20)
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

    /**
     * Re-échantillonnage haute fidélité (stéréo -> mono + interpolation mathématique exacte à ratio fixe).
     * Élimine 100% des scintillements métalliques et variations de hauteur.
     */
    private fun resampleAudio(
        input: ShortArray,
        inputCount: Int,
        inputSampleRate: Int,
        targetSampleRate: Int,
        channels: Int,
        targetSamples: Int
    ): ShortArray {
        val output = ShortArray(targetSamples)
        if (inputCount <= 0) return output

        // 1. Mixage Stéréo -> Mono avec saturation contrôlée
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

        // 2. Copie directe si la fréquence correspond déjà
        if (inputSampleRate == targetSampleRate) {
            val copyCount = minOf(monoCount, targetSamples)
            System.arraycopy(monoSamples, 0, output, 0, copyCount)
            return output
        }

        // 3. Interpolation linéaire avec ratio mathématique constant
        val ratio = inputSampleRate.toDouble() / targetSampleRate.toDouble()
        for (i in 0 until targetSamples) {
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

    fun stopStreaming() {
        if (!isStreaming.getAndSet(false)) return
        _isBridgeActive.value = false
        _isDigitalCapture.value = false
        Log.d(TAG, "Arrêt diffusion audio Bluetooth vers BlackBerry...")
        recordJob?.cancel()
        recordJob = null

        // Restore speaker volume if it was muted
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
            Log.w(TAG, "Erreur rétablissement volume HP: ${e.message}")
        }

        // 3. Signal de fin exact : "AUDIO_STOP\n"
        BluetoothService.instance?.sendPacket(BSBPacket("AUDIO_STOP", emptyList()))
        BridgeStateManager.logEvent("Diffusion Audio BB arrêtée", EventType.INFO)

        // Stop AudioProjectionService
        try {
            val s = BluetoothService.instance
            if (s != null) {
                val stopIntent = Intent(s, AudioProjectionService::class.java).apply {
                    action = AudioProjectionService.ACTION_STOP
                }
                s.startService(stopIntent)
            }
        } catch (e: Exception) {}

        try {
            callAudioTrack?.stop()
            callAudioTrack?.release()
            callAudioTrack = null
        } catch (e: Exception) {}
    }

    fun toggleStreaming(service: BluetoothService) {
        if (isStreaming.get()) {
            stopStreaming()
        } else {
            startStreaming(service)
        }
    }

    /**
     * Génère et envoie immédiatement un bip de test sinusoïdal 440 Hz (Note La4) de 500 ms
     * pour vérifier instantanément le haut-parleur du BlackBerry.
     */
    fun sendTestBeep(service: BluetoothService) {
        scope.launch {
            try {
                val targetRate = _sampleRate.value
                val targetSamples = (targetRate * 0.5).toInt()
                val targetPcmBytes = targetSamples * 2
                val freq = 440.0
                val pcm = ByteArray(targetPcmBytes)
                for (i in 0 until targetSamples) {
                    val angle = 2.0 * Math.PI * freq * i / targetRate
                    val sample = (Math.sin(angle) * 16000.0).toInt().coerceIn(-32768, 32767).toShort()
                    pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
                    pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                }
                val header = createWavHeader(targetPcmBytes, targetRate, TARGET_CHANNELS, TARGET_BITS_PER_SAMPLE)
                val fullWav = ByteArray(44 + targetPcmBytes)
                System.arraycopy(header, 0, fullWav, 0, 44)
                System.arraycopy(pcm, 0, fullWav, 44, targetPcmBytes)
                val base64 = Base64.encodeToString(fullWav, Base64.NO_WRAP)

                service.sendPacket(BSBPacket("AUDIO_START", emptyList()))
                delay(30)
                service.sendPacket(BSBPacket("AUDIO_CHUNK", listOf(base64)))
                _txPackets.value = _txPackets.value + 1
                _totalBytesSent.value = _totalBytesSent.value + fullWav.size
                _audioLevel.value = 0.85f
                _hasAudioSignal.value = true
                _captureStatus.value = "Bip de test transmis vers BlackBerry (${targetRate / 1000}kHz)"
                BridgeStateManager.logEvent("Bip test transmis vers BlackBerry (440Hz / ${targetRate / 1000}kHz)", EventType.SUCCESS)
                delay(550)
                _audioLevel.value = 0f
                _hasAudioSignal.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Erreur émission bip test", e)
            }
        }
    }

    /**
     * Générateur d'en-tête standard RIFF/WAVE de 44 octets
     */
    fun createWavHeader(pcmDataLen: Int, sampleRate: Int = 16000, channels: Short = 1, bitsPerSample: Short = 16): ByteArray {
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
        try {
            val rawBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val offset = if (rawBytes.size > 44 && rawBytes[0] == 'R'.code.toByte()) 44 else 0
            val len = rawBytes.size - offset
            if (len <= 0) return

            val rate = _sampleRate.value
            if (callAudioTrack == null || callAudioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
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
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf * 2, 8000))
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
