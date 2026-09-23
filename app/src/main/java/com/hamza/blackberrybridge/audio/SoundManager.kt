package com.hamza.blackberrybridge.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket
import com.hamza.blackberrybridge.state.BridgeStateManager
import java.lang.ref.WeakReference

object SoundManager {
    private const val TAG = "SoundManager"
    private const val ALARM_CHANNEL_ID = "find_phone_alarm_channel"
    private const val ALARM_NOTIFICATION_ID = 9999
    const val ACTION_STOP_ALARM = "com.hamza.blackberrybridge.ACTION_STOP_ALARM"

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    var activeActivity: WeakReference<FindPhoneActivity>? = null

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

            // Maximize volume for alarm
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)

            // Stop any previous instance
            stopFindPhone(context, notifyBlackBerry = false)

            // 1. Play Alarm with MediaPlayer using USAGE_ALARM (Not MediaStyle!)
            val alertUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(context, alertUri)
                isLooping = true
                prepare()
                start()
            }

            // 2. Start repeating vibration
            startVibration(context)

            // 3. Create high-priority alarm notification with Dismiss action
            showAlarmNotification(context)

            // 4. Launch full-screen FindPhoneActivity
            val activityIntent = Intent(context, FindPhoneActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(activityIntent)

            BridgeStateManager.setAlarmRinging(true)
            BridgeStateManager.logEvent("🚨 Alarme de localisation déclenchée", com.hamza.blackberrybridge.state.EventType.WARNING)
            Log.d(TAG, "Find phone triggered successfully with alarm notification and activity")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play find phone alarm", e)
            (context as? BluetoothService)?.sendPacket(BSBPacket("ERROR", listOf("FIND_PHONE_FAILED")))
        }
    }

    private fun startVibration(context: Context) {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 800, 300, 800, 300)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration error", e)
        }
    }

    private fun showAlarmNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Alarme de Localisation",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications d'alarme pour retrouver l'appareil"
                enableVibration(true)
                setSound(null, null) // Sound is produced directly by our MediaPlayer
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action intent to stop alarm
        val stopIntent = Intent(context, StopAlarmReceiver::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Fullscreen intent to open FindPhoneActivity
        val fullScreenIntent = Intent(context, FindPhoneActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            1002,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 Localisation en cours")
            .setContentText("Votre BlackBerry fait sonner cet appareil à volume maximal")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "ARRÊTER L'ALARME",
                stopPendingIntent
            )
            .build()

        notificationManager.notify(ALARM_NOTIFICATION_ID, notification)
    }

    fun stopFindPhone(context: Context? = null, notifyBlackBerry: Boolean = false) {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null

            vibrator?.cancel()
            vibrator = null

            context?.let { ctx ->
                val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(ALARM_NOTIFICATION_ID)
            }

            activeActivity?.get()?.finish()
            activeActivity = null

            BridgeStateManager.setAlarmRinging(false)

            if (notifyBlackBerry) {
                BluetoothService.instance?.sendPacket(BSBPacket("PHONE_FOUND", emptyList()))
                BluetoothService.instance?.sendPacket(BSBPacket("FIND_PHONE_STOPPED", emptyList()))
                BridgeStateManager.logEvent("Alarme arrêtée depuis le smartphone", com.hamza.blackberrybridge.state.EventType.SUCCESS)
            }
            Log.d(TAG, "Find phone stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping find phone", e)
        }
    }
}
