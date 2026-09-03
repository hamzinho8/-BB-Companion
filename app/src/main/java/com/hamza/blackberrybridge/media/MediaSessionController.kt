package com.hamza.blackberrybridge.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.util.Log
import android.view.KeyEvent
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.notification.BridgeNotificationListener
import com.hamza.blackberrybridge.protocol.BSBPacket

object MediaSessionController {
    private const val TAG = "MediaSessionController"

    private fun getActiveController(context: Context): MediaController? {
        try {
            val component = ComponentName(context, BridgeNotificationListener::class.java)
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            // This requires the NotificationListenerService to be bound and active
            val controllers = manager.getActiveSessions(component)
            return controllers.firstOrNull()
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Notification Listener permission for MediaSessions", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active media session", e)
            return null
        }
    }

    fun dispatchMediaCommand(context: Context, command: String) {
        val controller = getActiveController(context)
        if (controller == null) {
            Log.w(TAG, "No active media session found or permission missing")
            (context as? BluetoothService)?.sendPacket(BSBPacket("ERROR", listOf("MEDIA_CONTROL_UNAVAILABLE")))
            return
        }

        when (command) {
            "MEDIA_PLAY" -> controller.transportControls.play()
            "MEDIA_PAUSE" -> controller.transportControls.pause()
            "MEDIA_NEXT" -> controller.transportControls.skipToNext()
            "MEDIA_PREVIOUS" -> controller.transportControls.skipToPrevious()
        }
    }
}
