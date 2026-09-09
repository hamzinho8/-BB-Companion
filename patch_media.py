with open('app/src/main/java/com/hamza/blackberrybridge/media/MediaSessionController.kt', 'r') as f:
    content = f.read()

import re

# We need to replace the callback object completely to handle both metadata and state changes.

new_callback = """    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            sendMediaUpdate()
        }
        
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            super.onPlaybackStateChanged(state)
            sendMediaUpdate()
        }
    }

    private fun sendMediaUpdate() {
        val controller = currentController ?: return
        val metadata = controller.metadata
        val pbState = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown"
        
        val stateStr = when (pbState?.state) {
            android.media.session.PlaybackState.STATE_PLAYING -> "PLAYING"
            android.media.session.PlaybackState.STATE_PAUSED -> "PAUSED"
            android.media.session.PlaybackState.STATE_STOPPED -> "STOPPED"
            else -> "UNKNOWN"
        }

        com.hamza.blackberrybridge.state.BridgeStateManager.logEvent("Média: $title - $artist ($stateStr)", com.hamza.blackberrybridge.state.EventType.INFO)
        // FORMAT: MEDIA|Title|Artist|State
        BluetoothService.instance?.sendPacket(BSBPacket("MEDIA", listOf(title, artist, stateStr)))
    }"""

# regex to replace the old callback
content = re.sub(
    r'private val callback = object : MediaController\.Callback\(\) \{.*?(?=private val sessionListener =)',
    new_callback + '\n\n    ',
    content,
    flags=re.DOTALL
)

# replace the initial trigger in updateActiveController
content = content.replace(
    "currentController?.metadata?.let { callback.onMetadataChanged(it) }",
    "sendMediaUpdate()"
)

with open('app/src/main/java/com/hamza/blackberrybridge/media/MediaSessionController.kt', 'w') as f:
    f.write(content)

