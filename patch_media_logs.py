with open('app/src/main/java/com/hamza/blackberrybridge/media/MediaSessionController.kt', 'r') as f:
    content = f.read()

old_send = """            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown"
            BluetoothService.instance?.sendPacket(BSBPacket("MEDIA_META", listOf(title, artist)))"""

new_send = """            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown"
            com.hamza.blackberrybridge.state.BridgeStateManager.logEvent("Média détecté: $title - $artist", com.hamza.blackberrybridge.state.EventType.INFO)
            BluetoothService.instance?.sendPacket(BSBPacket("MEDIA_META", listOf(title, artist)))"""

content = content.replace(old_send, new_send)

with open('app/src/main/java/com/hamza/blackberrybridge/media/MediaSessionController.kt', 'w') as f:
    f.write(content)
