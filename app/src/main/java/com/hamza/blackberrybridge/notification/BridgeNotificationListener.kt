package com.hamza.blackberrybridge.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

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
    private val replyActions = ConcurrentHashMap<String, Notification.Action>()
    private val lastNotifTime = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected by system.")
        com.hamza.blackberrybridge.media.MediaSessionController.startListening(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected.")
        com.hamza.blackberrybridge.media.MediaSessionController.stopListening(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName == applicationContext.packageName) return

        // CRITICAL FIX: Filter out phone calls, dialers, telecom, and ongoing system notifications.
        // Google Dialer and Samsung InCallUI update their notification every 100ms with call duration timer,
        // which floods the Bluetooth link and causes BlackBerry OS process to crash (ANR / process terminated).
        if (sbn.isOngoing ||
            packageName.contains("dialer", ignoreCase = true) ||
            packageName.contains("telecom", ignoreCase = true) ||
            packageName.contains("incallui", ignoreCase = true) ||
            packageName.contains("phone", ignoreCase = true) ||
            packageName == "com.google.android.dialer" ||
            packageName == "com.android.phone") {
            return
        }

        val dataStore = com.hamza.blackberrybridge.settings.SettingsDataStore(applicationContext)
        val allowNotif = runBlocking { dataStore.notificationForwardingFlow.first() }
        if (!allowNotif) return

        val id = sbn.key
        activeNotifications[id] = sbn

        sbn.notification.actions?.forEach { action ->
            if (action.remoteInputs != null) {
                replyActions[id] = action
            }
        }

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

        val notifKey = "$packageName:$title:$text"
        val now = System.currentTimeMillis()
        val prev = lastNotifTime[notifKey] ?: 0L
        if (now - prev < 2500L) {
            return // Skip rapid duplicate notification
        }
        lastNotifTime[notifKey] = now

        // NOTIFICATION|notif_id|app|sender|message
        com.hamza.blackberrybridge.state.BridgeStateManager.logEvent("Notification interceptée: $appName", com.hamza.blackberrybridge.state.EventType.INFO)
        BluetoothService.instance?.sendPacket(BSBPacket("NOTIFICATION", listOf(id, appName, title, text)))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        activeNotifications.remove(sbn.key)
    }

    fun replyToMessage(notifId: String, replyText: String): Boolean {
        val action = replyActions[notifId] ?: return false
        val remoteInputs = action.remoteInputs ?: return false
        val intent = android.content.Intent()
        val bundle = android.os.Bundle()
        for (input in remoteInputs) {
            bundle.putCharSequence(input.resultKey, replyText)
        }
        android.app.RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
        try {
            action.actionIntent.send(this, 0, intent)
            Log.d(TAG, "Reply sent successfully for $notifId")
            com.hamza.blackberrybridge.state.BridgeStateManager.logEvent("Réponse envoyée: $replyText", com.hamza.blackberrybridge.state.EventType.SUCCESS)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send reply", e)
        }
        return false
    }

    fun replyWithAudio(notifId: String, audioFile: File): Boolean {
        // NIVEAU 1-3 : Android does not officially support pushing audio files through RemoteInput directly for most apps.
        // NIVEAU 4 : Would require AccessibilityService.
        // For now, we return unsupported to prevent simulating success.
        Log.w(TAG, "Audio reply requested for $notifId, but it is technically restricted by Android RemoteInput.")
        return false 
    }
}
