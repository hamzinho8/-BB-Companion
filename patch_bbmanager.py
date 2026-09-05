import re

with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BBBluetoothManager.kt', 'r') as f:
    content = f.read()

# 1. Add isIntentionalDisconnect flag
if "private var isIntentionalDisconnect = false" not in content:
    content = content.replace("private var outWriter: PrintWriter? = null", "private var outWriter: PrintWriter? = null\n    private var isIntentionalDisconnect = false")

# 2. Reset the flag on connect
content = content.replace("stopScanning()\n        connectionJob?.cancel()", "stopScanning()\n        connectionJob?.cancel()\n        isIntentionalDisconnect = false")

# 3. Set the flag on explicit disconnect
content = content.replace("connectionJob?.cancel()\n        try {", "isIntentionalDisconnect = true\n        connectionJob?.cancel()\n        try {")

# 4. Trigger alert in finally block
old_finally = """        } catch (e: Exception) {
            Log.e(TAG, "Connection lost", e)
        } finally {
            socket.close()
            activeSocket = null
            outWriter = null
            withContext(Dispatchers.Main) {
                service.updateNotification("○ BlackBerry déconnecté")
                BridgeStateManager.setConnected(false, null)
            }
        }"""

new_finally = """        } catch (e: Exception) {
            Log.e(TAG, "Connection lost", e)
        } finally {
            socket.close()
            activeSocket = null
            outWriter = null
            val wasIntentional = isIntentionalDisconnect
            withContext(Dispatchers.Main) {
                service.updateNotification("○ BlackBerry déconnecté")
                BridgeStateManager.setConnected(false, null)
                
                if (!wasIntentional) {
                    service.showDisconnectionAlert(deviceName)
                    BridgeStateManager.logEvent("Connexion perdue avec $deviceName", com.hamza.blackberrybridge.state.EventType.ERROR)
                }
            }
        }"""

content = content.replace(old_finally, new_finally)

with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BBBluetoothManager.kt', 'w') as f:
    f.write(content)
