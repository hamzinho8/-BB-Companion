with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BBBluetoothManager.kt', 'r') as f:
    content = f.read()

import re

# Add the mock RSSI logic in manageConnectedSocket
if 'launch {' not in content.split('manageConnectedSocket')[1]:
    # Need to find the space right after Handshake
    old_code = 'sendMessage("HELLO|BSB/1|ANDROID_DEVICE\\n")'
    new_code = """sendMessage("HELLO|BSB/1|ANDROID_DEVICE\\n")
            
            // Launch simulated real-time RSSI updates
            scope.launch {
                while (currentCoroutineContext().isActive && socket.isConnected) {
                    // Simulate RSSI between -40 (excellent) and -80 (weak)
                    val baseRssi = -55
                    val fluctuation = (-10..10).random()
                    BridgeStateManager.setRssiLevel(baseRssi + fluctuation)
                    delay(1500)
                }
            }"""
    content = content.replace(old_code, new_code)

with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BBBluetoothManager.kt', 'w') as f:
    f.write(content)
