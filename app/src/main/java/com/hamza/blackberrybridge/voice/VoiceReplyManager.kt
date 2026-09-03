package com.hamza.blackberrybridge.voice

import android.content.Context
import android.util.Base64
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket
import java.io.File
import java.io.FileOutputStream

object VoiceReplyManager {
    private const val TAG = "VoiceReplyManager"
    private const val MAX_BASE64_LENGTH = 1048576 // 1MB limit for safety

    fun handleVoiceReply(context: Context, notifId: String, base64Audio: String) {
        if (base64Audio.length > MAX_BASE64_LENGTH) {
            (context as? BluetoothService)?.sendPacket(BSBPacket("VOICE_REPLY_RESULT", listOf(notifId, "FAILED", "TOO_LARGE")))
            return
        }

        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            
            // Validate minimal AMR header (if expecting .amr)
            if (audioBytes.size < 6) {
                (context as? BluetoothService)?.sendPacket(BSBPacket("VOICE_REPLY_RESULT", listOf(notifId, "FAILED", "INVALID_FORMAT")))
                return
            }
            
            val cacheDir = context.cacheDir
            val audioFile = File(cacheDir, "voice_reply_${System.currentTimeMillis()}.amr")
            
            FileOutputStream(audioFile).use { fos ->
                fos.write(audioBytes)
            }
            
            Log.d(TAG, "Voice reply saved: ${audioFile.absolutePath}")
            
            // Try to send via Notification Listener.
            // In a real app, this requires level 1-4 fallback mechanism.
            val result = com.hamza.blackberrybridge.notification.BridgeNotificationListener.instance?.replyWithAudio(notifId, audioFile)
            
            if (result == true) {
                (context as? BluetoothService)?.sendPacket(BSBPacket("VOICE_REPLY_RESULT", listOf(notifId, "SUCCESS")))
            } else {
                (context as? BluetoothService)?.sendPacket(BSBPacket("VOICE_REPLY_RESULT", listOf(notifId, "UNSUPPORTED")))
            }
            
            // Cleanup after attempting to send
            audioFile.delete()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid Base64 string", e)
            (context as? BluetoothService)?.sendPacket(BSBPacket("VOICE_REPLY_RESULT", listOf(notifId, "FAILED", "BAD_BASE64")))
        } catch (e: Exception) {
            Log.e(TAG, "Error handling voice reply", e)
            (context as? BluetoothService)?.sendPacket(BSBPacket("VOICE_REPLY_RESULT", listOf(notifId, "FAILED", "EXCEPTION")))
        }
    }
}
