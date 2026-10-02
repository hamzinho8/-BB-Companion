package com.hamza.blackberrybridge.audio

import android.content.Context
import android.media.projection.MediaProjection
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Passerelle d'appel et d'écoute audio vers le BlackBerry Curve 9300.
 * Délégué direct vers [AudioStreamManager] configuré en 16000 Hz HD Voice / 200 ms.
 */
object CallAudioBridge {
    const val TARGET_SAMPLE_RATE = AudioStreamManager.SAMPLE_RATE
    const val PCM_CHUNK_SIZE = AudioStreamManager.PCM_CHUNK_BYTES
    const val TARGET_SAMPLES = AudioStreamManager.SAMPLES_PER_CHUNK
    const val TARGET_CHANNELS: Short = AudioStreamManager.CHANNELS
    const val TARGET_BITS_PER_SAMPLE: Short = AudioStreamManager.BITS_PER_SAMPLE

    private val _sampleRate = MutableStateFlow(AudioStreamManager.SAMPLE_RATE)
    val sampleRate: StateFlow<Int> = _sampleRate.asStateFlow()

    private val _isVoipEnabled = MutableStateFlow(true)
    val isVoipEnabled: StateFlow<Boolean> = _isVoipEnabled.asStateFlow()

    val txPackets: StateFlow<Int> get() = AudioStreamManager.txPackets
    val rxPackets: StateFlow<Int> get() = AudioStreamManager.rxPackets
    val isBridgeActive: StateFlow<Boolean> get() = AudioStreamManager.isStreamingFlow
    val isDigitalCapture: StateFlow<Boolean> get() = AudioStreamManager.isDigitalCapture
    val isSpeakerMuted: StateFlow<Boolean> get() = AudioStreamManager.isSpeakerMuted
    val audioLevel: StateFlow<Float> get() = AudioStreamManager.audioLevel
    val hasAudioSignal: StateFlow<Boolean> get() = AudioStreamManager.hasAudioSignal
    val captureStatus: StateFlow<String> get() = AudioStreamManager.captureStatus
    val totalBytesSent: StateFlow<Long> get() = AudioStreamManager.totalBytesSent

    var activeMediaProjection: MediaProjection?
        get() = AudioStreamManager.activeMediaProjection
        set(value) {
            AudioStreamManager.activeMediaProjection = value
        }

    fun setVoipEnabled(enabled: Boolean) {
        _isVoipEnabled.value = enabled
        if (!enabled && isBridgeActive.value) {
            stopStreaming(stopProjectionService = true)
        }
    }

    fun setSampleRate(rate: Int) {
        _sampleRate.value = rate
    }

    fun startStreaming(service: BluetoothService) {
        AudioStreamManager.start(service)
    }

    fun stopStreaming(stopProjectionService: Boolean = true) {
        AudioStreamManager.stop(stopProjectionService)
    }

    fun toggleStreaming(service: BluetoothService) {
        if (isBridgeActive.value) {
            stopStreaming(stopProjectionService = true)
        } else {
            startStreaming(service)
        }
    }

    fun onCallStarted(service: BluetoothService) {
        AudioStreamManager.onCallStarted(service)
    }

    fun onCallEnded(service: BluetoothService) {
        AudioStreamManager.onCallEnded(service)
    }

    fun toggleSpeakerMute(context: Context) {
        AudioStreamManager.toggleSpeakerMute(context)
    }

    fun sendTestBeep(service: BluetoothService) {
        AudioStreamManager.sendTestBeep(service)
    }

    fun createWavHeader(
        pcmDataLen: Int = AudioStreamManager.PCM_CHUNK_BYTES,
        sampleRate: Int = AudioStreamManager.SAMPLE_RATE,
        channels: Short = AudioStreamManager.CHANNELS,
        bitsPerSample: Short = AudioStreamManager.BITS_PER_SAMPLE
    ): ByteArray {
        return AudioStreamManager.createRiffWavHeader(pcmDataLen)
    }

    fun playIncomingVoice(context: Context, base64Data: String) {
        AudioStreamManager.playIncomingVoice(context, base64Data)
    }
}
