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
 * Universal Bluetooth Earphone Audio Bridge.
 * Captures Android digital media audio (YouTube, Spotify, Music, Games)
 * or in-call microphone, downsamples to 8000Hz 16-bit Mono,
 * mutes the phone speaker (Earphones Mode), and streams to BlackBerry Curve 9300.
 */
object CallAudioBridge {
    private const val TAG = "CallAudioBridge"
    const val TARGET_SAMPLE_RATE = 8000
    const val TARGET_CHANNELS: Short = 1
    const val TARGET_BITS_PER_SAMPLE: Short = 16

    // Exact 500ms chunk at 8000Hz 16-bit Mono = 4000 samples = 8000 bytes PCM
    const val PCM_CHUNK_SIZE = 8000
    const val TARGET_SAMPLES = 4000

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

    private val _isSpeakerMuted = MutableStateFlow(true)
    val isSpeakerMuted: StateFlow<Boolean> = _isSpeakerMuted.asStateFlow()

    // Holds MediaProjection for Android 10+ internal digital audio capture
    var activeMediaProjection: MediaProjection? = null
    private var savedMediaVolume = -1

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
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                BridgeStateManager.logEvent("Haut-parleur smartphone coupé (Mode Écouteurs)", EventType.INFO)
            } else {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                BridgeStateManager.logEvent("Haut-parleur smartphone rétabli", EventType.INFO)
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

        // Optionally mute the phone speaker like real Bluetooth earphones
        if (_isSpeakerMuted.value) {
            try {
                val am = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (am != null) {
                    savedMediaVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                    Log.d(TAG, "Haut-parleur smartphone coupé pour mode écouteurs")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Impossible de couper le HP: ${e.message}")
            }
        }

        Log.d(TAG, "Démarrage diffusion audio Bluetooth vers BlackBerry...")
        BridgeStateManager.logEvent("Diffusion Audio BB active (WAV 500ms)", EventType.INFO)

        // 1. Signal de début exact : "AUDIO_START\n"
        service.sendPacket(BSBPacket("AUDIO_START", emptyList()))

        recordJob = scope.launch {
            var audioRecord: AudioRecord? = null
            var captureRate = TARGET_SAMPLE_RATE
            var captureChannels = 1
            var isDigital = false

            try {
                // 1. Try Android 10+ Internal Digital Media Playback Capture FIRST
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
                        Triple(44100, AudioFormat.CHANNEL_IN_MONO, 1)
                    )

                    for ((sr, chMask, chCount) in configs) {
                        try {
                            val minBuf = AudioRecord.getMinBufferSize(sr, chMask, AudioFormat.ENCODING_PCM_16BIT)
                            if (minBuf <= 0) continue
                            val ar = AudioRecord.Builder()
                                .setAudioPlaybackCaptureConfig(captureConfig)
                                .setAudioFormat(
                                    AudioFormat.Builder()
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setSampleRate(sr)
                                        .setChannelMask(chMask)
                                        .build()
                                )
                                .setBufferSizeInBytes(maxOf(minBuf, sr * chCount))
                                .build()

                            if (ar.state == AudioRecord.STATE_INITIALIZED) {
                                audioRecord = ar
                                captureRate = sr
                                captureChannels = chCount
                                isDigital = true
                                Log.d(TAG, "AudioPlaybackCapture (Son Numérique YouTube/Musique) INITIALISÉ : ${sr}Hz ${chCount}ch")
                                BridgeStateManager.logEvent("Audio Numérique YouTube/Musique connecté (${sr}Hz)", EventType.SUCCESS)
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

                // 2. Fallback to physical microphone if MediaProjection is not active
                if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "Bascule sur microphone physique (fallback)")
                    captureRate = TARGET_SAMPLE_RATE
                    captureChannels = 1
                    val minBuf = AudioRecord.getMinBufferSize(TARGET_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    val bufferSize = maxOf(minBuf, PCM_CHUNK_SIZE * 4)
                    val sources = listOf(
                        MediaRecorder.AudioSource.CAMCORDER,
                        MediaRecorder.AudioSource.MIC,
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        MediaRecorder.AudioSource.DEFAULT
                    )

                    for (source in sources) {
                        try {
                            val ar = AudioRecord(source, TARGET_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                            if (ar.state == AudioRecord.STATE_INITIALIZED) {
                                audioRecord = ar
                                Log.d(TAG, "AudioRecord initialisé avec la source micro: $source")
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

                    // Samples needed for 500 ms at current capture rate
                    val frameCountFor500ms = (captureRate * 0.5).toInt()
                    val totalShortsFor500ms = frameCountFor500ms * captureChannels
                    val captureBuffer = ShortArray(totalShortsFor500ms)
                    val txCounter = AtomicInteger(0)

                    while (isStreaming.get() && isActive && BridgeStateManager.isConnected.value) {
                        var shortsRead = 0
                        while (shortsRead < totalShortsFor500ms && isStreaming.get() && isActive && BridgeStateManager.isConnected.value) {
                            val r = audioRecord.read(captureBuffer, shortsRead, totalShortsFor500ms - shortsRead)
                            if (r > 0) {
                                shortsRead += r
                            } else if (r == 0) {
                                delay(10)
                            } else {
                                Log.w(TAG, "AudioRecord code retour: $r")
                                delay(25)
                                try {
                                    if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                                        audioRecord.startRecording()
                                    }
                                } catch (e: Exception) {}
                                break
                            }
                        }

                        if (shortsRead > 0) {
                            // Downsample to exactly 4000 shorts (8000Hz 16-bit Mono)
                            val downsampled = downsampleTo8000(captureBuffer, shortsRead, captureRate, captureChannels)

                            // Convert 4000 shorts to 8000 bytes Little-Endian
                            val pcmBytes = ByteArray(PCM_CHUNK_SIZE)
                            for (i in 0 until TARGET_SAMPLES) {
                                val s = downsampled[i].toInt()
                                pcmBytes[i * 2] = (s and 0xFF).toByte()
                                pcmBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                            }

                            // Assemblage du fichier WAV complet (44 octets en-tête + 8000 octets PCM = 8044 octets)
                            val wavHeader = createWavHeader(PCM_CHUNK_SIZE, TARGET_SAMPLE_RATE, TARGET_CHANNELS, TARGET_BITS_PER_SAMPLE)
                            val fullWav = ByteArray(44 + PCM_CHUNK_SIZE)
                            System.arraycopy(wavHeader, 0, fullWav, 0, 44)
                            System.arraycopy(pcmBytes, 0, fullWav, 44, PCM_CHUNK_SIZE)

                            // Encodage Base64 strict NO_WRAP
                            val base64 = Base64.encodeToString(fullWav, Base64.NO_WRAP)

                            // Envoi Bluetooth : "AUDIO_CHUNK|<base64_wav>\n"
                            service.sendPacket(BSBPacket("AUDIO_CHUNK", listOf(base64)))

                            val count = txCounter.incrementAndGet()
                            _txPackets.value = count
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
     * Downsamples any input PCM audio stream (48000Hz/44100Hz Stereo/Mono)
     * to exactly 4000 samples at 8000Hz Mono using linear interpolation and stereo mixdown.
     */
    private fun downsampleTo8000(input: ShortArray, inputCount: Int, inputSampleRate: Int, channels: Int): ShortArray {
        val output = ShortArray(TARGET_SAMPLES)
        if (inputCount <= 0) return output

        // 1. Stereo to Mono mixdown
        val monoSamples: ShortArray
        val monoCount: Int
        if (channels == 2) {
            monoCount = inputCount / 2
            monoSamples = ShortArray(monoCount)
            for (i in 0 until monoCount) {
                val l = input[i * 2].toInt()
                val r = input[i * 2 + 1].toInt()
                monoSamples[i] = ((l + r) / 2).toShort()
            }
        } else {
            monoCount = inputCount
            monoSamples = input
        }

        if (monoCount <= 0) return output

        // 2. Direct copy if already 8000Hz
        if (inputSampleRate == TARGET_SAMPLE_RATE) {
            val copyCount = minOf(monoCount, TARGET_SAMPLES)
            System.arraycopy(monoSamples, 0, output, 0, copyCount)
            return output
        }

        // 3. Resample via linear interpolation
        val step = monoCount.toDouble() / TARGET_SAMPLES.toDouble()
        for (i in 0 until TARGET_SAMPLES) {
            val srcPos = i * step
            val idx = srcPos.toInt()
            val frac = srcPos - idx
            val s0 = monoSamples[minOf(idx, monoCount - 1)].toDouble()
            val s1 = monoSamples[minOf(idx + 1, monoCount - 1)].toDouble()
            output[i] = (s0 + frac * (s1 - s0)).toInt().coerceIn(-32768, 32767).toShort()
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
