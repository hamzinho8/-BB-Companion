package com.hamza.blackberrybridge.audio

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket

object SoundManager {
    private const val TAG = "SoundManager"
    private var currentRingtone: Ringtone? = null

    // STATUS: PARTIALLY_SUPPORTED
    // Reason: We can play sounds and change volume, but overriding "Do Not Disturb" (DND)
    // requires ACCESS_NOTIFICATION_POLICY permission explicitly granted by the user.
    // If DND is on and permission is missing, the sound might be silenced.
    fun findPhone(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Check DND bypass permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                } else {
                    Log.w(TAG, "Cannot override DND: ACCESS_NOTIFICATION_POLICY not granted.")
                }
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }

            // Maximize volume
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)

            // Play Ringtone
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) 
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            currentRingtone?.stop()
            currentRingtone = RingtoneManager.getRingtone(context, uri)
            currentRingtone?.streamType = AudioManager.STREAM_ALARM
            currentRingtone?.play()
            
            Log.d(TAG, "Find phone triggered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play find phone alarm", e)
            (context as? BluetoothService)?.sendPacket(BSBPacket("ERROR", listOf("FIND_PHONE_FAILED")))
        }
    }

    fun stopFindPhone() {
        try {
            currentRingtone?.stop()
            currentRingtone = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone", e)
        }
    }
}
