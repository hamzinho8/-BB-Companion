with open('app/src/main/java/com/hamza/blackberrybridge/state/BridgeStateManager.kt', 'r') as f:
    content = f.read()

if 'val rssiLevel:' not in content:
    content = content.replace(
        'val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()',
        'val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()\n    private val _rssiLevel = MutableStateFlow<Int?>(null)\n    val rssiLevel: StateFlow<Int?> = _rssiLevel.asStateFlow()'
    )
    
    content = content.replace(
        'fun setBatteryLevel(level: Int) {',
        'fun setRssiLevel(level: Int?) {\n        _rssiLevel.value = level\n    }\n    fun setBatteryLevel(level: Int) {'
    )
    
    content = content.replace(
        'if (!connected) _batteryLevel.value = null',
        'if (!connected) {\n            _batteryLevel.value = null\n            _rssiLevel.value = null\n        }'
    )

with open('app/src/main/java/com/hamza/blackberrybridge/state/BridgeStateManager.kt', 'w') as f:
    f.write(content)
