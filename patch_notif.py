with open('app/src/main/java/com/hamza/blackberrybridge/notification/BridgeNotificationListener.kt', 'r') as f:
    content = f.read()

old_logic = """        val dataStore = com.hamza.blackberrybridge.settings.SettingsDataStore(applicationContext)
        val allowNotif = runBlocking { dataStore.notificationForwardingFlow.first() }
        if (!allowNotif) return

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
        com.hamza.blackberrybridge.state.BridgeStateManager.logEvent("Notification interceptée: $appName", com.hamza.blackberrybridge.state.EventType.INFO)
        BluetoothService.instance?.sendPacket(BSBPacket("NOTIFICATION", listOf(id, appName, title, text)))"""

new_logic = """        val dataStore = com.hamza.blackberrybridge.settings.SettingsDataStore(applicationContext)
        
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

        // Détection si la notification est un SMS/Message
        val isMessage = sbn.notification.category == Notification.CATEGORY_MESSAGE ||
                packageName.contains("messaging", ignoreCase = true) ||
                packageName.contains("mms", ignoreCase = true) ||
                packageName.contains("sms", ignoreCase = true) ||
                packageName.contains("whatsapp", ignoreCase = true)

        if (isMessage) {
            val allowSms = runBlocking { dataStore.allowSmsFlow.first() }
            if (allowSms) {
                com.hamza.blackberrybridge.state.BridgeStateManager.logEvent("SMS reçu de: $title", com.hamza.blackberrybridge.state.EventType.INFO)
                // FORMAT: SMS|Sender|Body
                BluetoothService.instance?.sendPacket(BSBPacket("SMS", listOf(title, text)))
            }
        } else {
            val allowNotif = runBlocking { dataStore.notificationForwardingFlow.first() }
            if (allowNotif) {
                com.hamza.blackberrybridge.state.BridgeStateManager.logEvent("Notification: $appName", com.hamza.blackberrybridge.state.EventType.INFO)
                // FORMAT: NOTIFICATION|notif_id|app|sender|message
                BluetoothService.instance?.sendPacket(BSBPacket("NOTIFICATION", listOf(id, appName, title, text)))
            }
        }"""

content = content.replace(old_logic, new_logic)

with open('app/src/main/java/com/hamza/blackberrybridge/notification/BridgeNotificationListener.kt', 'w') as f:
    f.write(content)
