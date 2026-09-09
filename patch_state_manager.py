with open('app/src/main/java/com/hamza/blackberrybridge/state/BridgeStateManager.kt', 'r') as f:
    content = f.read()

# Add TX and RX to EventType
content = content.replace(
    'enum class EventType {\n    INFO, SUCCESS, WARNING, ERROR\n}',
    'enum class EventType {\n    INFO, SUCCESS, WARNING, ERROR, TX, RX\n}'
)

# Use milliseconds for better log precision and increase limit to 500
content = content.replace(
    'private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())',
    'private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())'
)

content = content.replace(
    'if (current.size > 100) {',
    'if (current.size > 500) {'
)

with open('app/src/main/java/com/hamza/blackberrybridge/state/BridgeStateManager.kt', 'w') as f:
    f.write(content)
