package com.hamza.blackberrybridge.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class BridgeNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "BridgeNotificationListener"
        var instance: BridgeNotificationListener? = null
            private set
    }

    private val activeNotifications = ConcurrentHashMap<String, StatusBarNotification>()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName == applicationContext.packageName) return

        val id = sbn.key
        activeNotifications[id] = sbn

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val appName = try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }

        // NOTIFICATION|notif_id|app|sender|message
        BluetoothService.instance?.sendPacket(BSBPacket("NOTIFICATION", listOf(id, appName, title, text)))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        activeNotifications.remove(sbn.key)
    }

    fun replyWithAudio(notifId: String, audioFile: File): Boolean {
        // NIVEAU 1-3 : Android does not officially support pushing audio files through RemoteInput directly for most apps.
        // NIVEAU 4 : Would require AccessibilityService.
        // For now, we return unsupported to prevent simulating success.
        Log.w(TAG, "Audio reply requested for $notifId, but it is technically restricted by Android RemoteInput.")
        return false 
    }
}
