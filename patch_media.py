with open('app/src/main/java/com/hamza/blackberrybridge/media/MediaSessionController.kt', 'r') as f:
    content = f.read()

new_content = """package com.hamza.blackberrybridge.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.notification.BridgeNotificationListener
import com.hamza.blackberrybridge.protocol.BSBPacket

object MediaSessionController {
    private const val TAG = "MediaSessionController"
    private var currentController: MediaController? = null
    
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            if (metadata == null) return
            
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown"
            BluetoothService.instance?.sendPacket(BSBPacket("MEDIA_META", listOf(title, artist)))
        }
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
        currentController?.metadata?.let { callback.onMetadataChanged(it) }
    }

    fun startListening(context: Context) {
        try {
            val component = ComponentName(context, BridgeNotificationListener::class.java)
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            manager.addOnActiveSessionsChangedListener(sessionListener, component)
            val controllers = manager.getActiveSessions(component)
            updateActiveController(controllers.firstOrNull())
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Notification Listener permission for MediaSessions", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting media session listening", e)
        }
    }

    fun stopListening(context: Context) {
        try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            manager.removeOnActiveSessionsChangedListener(sessionListener)
            currentController?.unregisterCallback(callback)
            currentController = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media session listening", e)
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
"""

with open('app/src/main/java/com/hamza/blackberrybridge/media/MediaSessionController.kt', 'w') as f:
    f.write(new_content)
