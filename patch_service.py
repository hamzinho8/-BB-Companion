with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BluetoothService.kt', 'r') as f:
    content = f.read()

import re

if 'MediaSessionController.startListening(this)' not in content:
    content = content.replace('batteryManager?.startMonitoring()', 'batteryManager?.startMonitoring()\n        com.hamza.blackberrybridge.media.MediaSessionController.startListening(this)')

if 'MediaSessionController.stopListening(this)' not in content:
    content = content.replace('batteryManager?.stopMonitoring()', 'batteryManager?.stopMonitoring()\n        com.hamza.blackberrybridge.media.MediaSessionController.stopListening(this)')

with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BluetoothService.kt', 'w') as f:
    f.write(content)
