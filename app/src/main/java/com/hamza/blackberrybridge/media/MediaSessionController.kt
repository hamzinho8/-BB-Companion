package com.hamza.blackberrybridge.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.notification.BridgeNotificationListener
import com.hamza.blackberrybridge.protocol.BSBPacket

object MediaSessionController {
    private const val TAG = "MediaSessionController"
    private var currentController: MediaController? = null
    private var isListening = false

    fun hasNotificationAccess(context: Context): Boolean {
        return try {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }
    
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            sendMediaUpdate()
        }
        
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            super.onPlaybackStateChanged(state)
            sendMediaUpdate()
        }
    }

    private fun sendMediaUpdate() {
        val controller = currentController ?: return
        val metadata = controller.metadata
        val pbState = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown"
        
        val stateStr = when (pbState?.state) {
            android.media.session.PlaybackState.STATE_PLAYING -> "PLAYING"
            android.media.session.PlaybackState.STATE_PAUSED -> "PAUSED"
            android.media.session.PlaybackState.STATE_STOPPED -> "STOPPED"
            else -> "UNKNOWN"
        }

        com.hamza.blackberrybridge.state.BridgeStateManager.logEvent("Média: $title - $artist ($stateStr)", com.hamza.blackberrybridge.state.EventType.INFO)
        // FORMAT: MEDIA|Title|Artist|State
        BluetoothService.instance?.sendPacket(BSBPacket("MEDIA", listOf(title, artist, stateStr)))
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveController(controllers?.firstOrNull())
    }

    private fun updateActiveController(controller: MediaController?) {
        if (currentController == controller) return
        currentController?.unregisterCallback(callback)
        currentController = controller
        currentController?.registerCallback(callback)
        // trigger initial
        sendMediaUpdate()
    }

    fun startListening(context: Context) {
        if (!hasNotificationAccess(context)) {
            Log.d(TAG, "Notification listener permission not yet granted; media session control is paused until authorized.")
            return
        }
        if (isListening) return

        try {
            val component = ComponentName(context, BridgeNotificationListener::class.java)
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            if (manager != null) {
                manager.addOnActiveSessionsChangedListener(sessionListener, component)
                isListening = true
                val controllers = manager.getActiveSessions(component)
                updateActiveController(controllers.firstOrNull())
                Log.d(TAG, "MediaSession listening started successfully.")
            }
        } catch (e: SecurityException) {
            Log.d(TAG, "Notification listener permission pending in settings for MediaSessions: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Error starting media session listening: ${e.message}")
        }
    }

    fun stopListening(context: Context) {
        if (!isListening) {
            currentController?.unregisterCallback(callback)
            currentController = null
            return
        }
        try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            manager?.removeOnActiveSessionsChangedListener(sessionListener)
            currentController?.unregisterCallback(callback)
            currentController = null
            isListening = false
            Log.d(TAG, "MediaSession listening stopped.")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping media session listening: ${e.message}")
        }
    }

    fun dispatchMediaCommand(context: Context, command: String) {
        if (currentController == null) {
            Log.w(TAG, "No active media session found")
            (context as? BluetoothService)?.sendPacket(BSBPacket("ERROR", listOf("MEDIA_CONTROL_UNAVAILABLE")))
            return
        }
        when (command) {
            "MEDIA_PLAY" -> currentController?.transportControls?.play()
            "MEDIA_PAUSE" -> currentController?.transportControls?.pause()
            "MEDIA_NEXT" -> currentController?.transportControls?.skipToNext()
            "MEDIA_PREVIOUS" -> currentController?.transportControls?.skipToPrevious()
        }
    }
}
