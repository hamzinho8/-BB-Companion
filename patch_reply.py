with open('app/src/main/java/com/hamza/blackberrybridge/notification/BridgeNotificationListener.kt', 'r') as f:
    content = f.read()

# 1. Add replyActions map
content = content.replace(
    "private val activeNotifications = ConcurrentHashMap<String, StatusBarNotification>()",
    "private val activeNotifications = ConcurrentHashMap<String, StatusBarNotification>()\n    private val replyActions = ConcurrentHashMap<String, Notification.Action>()"
)

# 2. Add action extraction
extraction = """        val id = sbn.key
        activeNotifications[id] = sbn

        sbn.notification.actions?.forEach { action ->
            if (action.remoteInputs != null) {
                replyActions[id] = action
            }
        }"""
content = content.replace("""        val id = sbn.key
        activeNotifications[id] = sbn""", extraction)

# 3. Update SMS packet format
content = content.replace(
    """BSBPacket("SMS", listOf(title, text))""",
    """BSBPacket("SMS", listOf(id, title, text))"""
)

# 4. Add replyToMessage function
reply_func = """    fun replyWithAudio(notifId: String, audioFile: File): Boolean {"""

new_reply_func = """    fun replyToMessage(notifId: String, replyText: String): Boolean {
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

    fun replyWithAudio(notifId: String, audioFile: File): Boolean {"""
content = content.replace(reply_func, new_reply_func)

with open('app/src/main/java/com/hamza/blackberrybridge/notification/BridgeNotificationListener.kt', 'w') as f:
    f.write(content)
