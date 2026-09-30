package com.hamza.blackberrybridge.audio

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hamza.blackberrybridge.R
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.state.EventType

/**
 * Dedicated Foreground Service for MediaProjection on Android 14+ (API 34+).
 * Required by Android 14 security policy: startForeground with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
 * MUST be executed inside onStartCommand before getMediaProjection is invoked.
 */
class AudioProjectionService : Service() {
    companion object {
        private const val TAG = "AudioProjectionService"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "bb_audio_projection_channel"

        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val ACTION_STOP = "com.hamza.blackberrybridge.action.STOP_AUDIO_PROJECTION"

        var instance: AudioProjectionService? = null
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "BBBridge:AudioProjectionWakeLock").apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // Empêche la mise en veille CPU en arrière-plan
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock exception: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopProjection()
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            try {
                val notification = buildNotification("Capture audio numérique active")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val mp = mpm.getMediaProjection(resultCode, resultData)
                if (mp != null) {
                    mp.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            Log.d(TAG, "MediaProjection arrêté")
                            CallAudioBridge.stopStreaming()
                        }
                    }, android.os.Handler(android.os.Looper.getMainLooper()))
                    mediaProjection = mp
                    CallAudioBridge.activeMediaProjection = mp
                    Log.d(TAG, "MediaProjection initialisé et callback enregistré !")
                    BridgeStateManager.logEvent("MediaProjection validé (Pur Numérique) !", EventType.SUCCESS)
                } else {
                    Log.e(TAG, "MediaProjectionManager.getMediaProjection a retourné null")
                    CallAudioBridge.activeMediaProjection = null
                    BridgeStateManager.logEvent("MediaProjection retourné null par le système", EventType.ERROR)
                }

                // Start Bluetooth streaming
                BluetoothService.instance?.let { service ->
                    CallAudioBridge.startStreaming(service)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Erreur initialisation MediaProjection: ${t.message}", t)
                CallAudioBridge.activeMediaProjection = null
                BridgeStateManager.logEvent("Erreur init MediaProjection: ${t.message}", EventType.ERROR)
            }
        } else {
            Log.w(TAG, "Données de projection manquantes ou invalides")
            BridgeStateManager.logEvent("Données de projection manquantes (code: $resultCode)", EventType.WARNING)
            BluetoothService.instance?.let { service ->
                CallAudioBridge.startStreaming(service)
            }
        }

        return START_NOT_STICKY
    }

    fun stopProjection() {
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {}
        mediaProjection = null
        CallAudioBridge.activeMediaProjection = null
    }

    override fun onDestroy() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {}
        super.onDestroy()
        stopProjection()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Diffusion Audio Numérique BlackBerry",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Écouteurs BlackBerry Curve 9300")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
