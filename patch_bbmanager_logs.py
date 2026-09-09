with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BBBluetoothManager.kt', 'r') as f:
    content = f.read()

# Enhance send message logging
old_send = """    fun sendMessage(message: String) {
        scope.launch {
            try {
                outWriter?.print(message)
                outWriter?.flush()
                Log.d(TAG, "Sent: $message")
            } catch (e: Exception) {"""

new_send = """    fun sendMessage(message: String) {
        scope.launch {
            try {
                outWriter?.print(message)
                outWriter?.flush()
                Log.d(TAG, "Sent: $message")
                BridgeStateManager.logEvent(message.trim(), com.hamza.blackberrybridge.state.EventType.TX)
            } catch (e: Exception) {"""

content = content.replace(old_send, new_send)

# Enhance receive message logging
old_receive = """            while (currentCoroutineContext().isActive && socket.isConnected) {
                val line = reader.readLine() ?: break
                Log.d(TAG, "Received: $line")
                
                val packet = CommandParser.parse(line)"""

new_receive = """            while (currentCoroutineContext().isActive && socket.isConnected) {
                val line = reader.readLine() ?: break
                Log.d(TAG, "Received: $line")
                BridgeStateManager.logEvent(line.trim(), com.hamza.blackberrybridge.state.EventType.RX)
                
                val packet = CommandParser.parse(line)"""

content = content.replace(old_receive, new_receive)

with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BBBluetoothManager.kt', 'w') as f:
    f.write(content)
