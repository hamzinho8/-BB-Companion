with open('app/src/main/java/com/hamza/blackberrybridge/notification/BridgeNotificationListener.kt', 'r') as f:
    content = f.read()

old_send = """        // NOTIFICATION|notif_id|app|sender|message
        BluetoothService.instance?.sendPacket(BSBPacket("NOTIFICATION", listOf(id, appName, title, text)))"""

new_send = """        // NOTIFICATION|notif_id|app|sender|message
        com.hamza.blackberrybridge.state.BridgeStateManager.logEvent("Notification interceptée: $appName", com.hamza.blackberrybridge.state.EventType.INFO)
        BluetoothService.instance?.sendPacket(BSBPacket("NOTIFICATION", listOf(id, appName, title, text)))"""

content = content.replace(old_send, new_send)

with open('app/src/main/java/com/hamza/blackberrybridge/notification/BridgeNotificationListener.kt', 'w') as f:
    f.write(content)
