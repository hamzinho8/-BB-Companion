with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BBBluetoothManager.kt', 'r') as f:
    content = f.read()

import re

# We need to save the last device
if 'private var lastDevice:' not in content:
    content = content.replace('private var connectionJob: Job? = null', 'private var connectionJob: Job? = null\n    private var reconnectJob: Job? = null\n    private var lastDevice: BluetoothDevice? = null\n    private var lastService: BluetoothService? = null')

# Update connectToDevice
if 'lastDevice = device' not in content:
    content = content.replace('isIntentionalDisconnect = false', 'isIntentionalDisconnect = false\n        reconnectJob?.cancel()\n        lastDevice = device\n        lastService = service')

# In manageConnectedSocket, handle the retry
old_finally = """            val wasIntentional = isIntentionalDisconnect
            withContext(Dispatchers.Main) {
                service.updateNotification("○ BlackBerry déconnecté")
                BridgeStateManager.setConnected(false, null)
                
                if (!wasIntentional) {
                    service.showDisconnectionAlert(deviceName)
                    BridgeStateManager.logEvent("Connexion perdue avec $deviceName", com.hamza.blackberrybridge.state.EventType.ERROR)
                }
            }"""

new_finally = """            val wasIntentional = isIntentionalDisconnect
            withContext(Dispatchers.Main) {
                service.updateNotification("○ BlackBerry déconnecté")
                BridgeStateManager.setConnected(false, null)
                
                if (!wasIntentional) {
                    service.showDisconnectionAlert(deviceName)
                    BridgeStateManager.logEvent("Connexion perdue avec $deviceName, tentative de reconnexion...", com.hamza.blackberrybridge.state.EventType.ERROR)
                    attemptReconnect()
                }
            }"""

content = content.replace(old_finally, new_finally)

# Also in connectToDevice, handle catch block retry
old_catch = """            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                withContext(Dispatchers.Main) {
                    service.updateNotification("❌ Échec de la connexion")
                    BridgeStateManager.logEvent("Échec de la connexion", com.hamza.blackberrybridge.state.EventType.ERROR)
                }
            }"""

new_catch = """            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                withContext(Dispatchers.Main) {
                    service.updateNotification("❌ Échec de la connexion")
                    BridgeStateManager.logEvent("Échec de la connexion", com.hamza.blackberrybridge.state.EventType.ERROR)
                    if (!isIntentionalDisconnect) {
                        attemptReconnect()
                    }
                }
            }"""

content = content.replace(old_catch, new_catch)

# Add attemptReconnect method
if 'fun attemptReconnect' not in content:
    reconnect_func = """
    private fun attemptReconnect() {
        if (isIntentionalDisconnect) return
        val device = lastDevice ?: return
        val service = lastService ?: return
        
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            BridgeStateManager.logEvent("Reconnexion dans 5s...", com.hamza.blackberrybridge.state.EventType.WARNING)
            delay(5000)
            if (!isIntentionalDisconnect) {
                BridgeStateManager.logEvent("Nouvelle tentative de connexion à ${device.name ?: device.address}", com.hamza.blackberrybridge.state.EventType.INFO)
                connectToDevice(device, service)
            }
        }
    }
"""
    content = content.replace('fun disconnect() {', reconnect_func + '\n    fun disconnect() {')
    
# Disconnect resets it
content = content.replace('isIntentionalDisconnect = true\n        connectionJob?.cancel()', 'isIntentionalDisconnect = true\n        connectionJob?.cancel()\n        reconnectJob?.cancel()')


with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BBBluetoothManager.kt', 'w') as f:
    f.write(content)
