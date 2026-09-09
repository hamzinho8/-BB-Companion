with open('app/src/main/java/com/hamza/blackberrybridge/protocol/CommandDispatcher.kt', 'r') as f:
    content = f.read()

old_battery = """            "BATTERY", "PHONE_BATTERY" -> {
                if (packet.args.isNotEmpty()) {
                    packet.args[0].toIntOrNull()?.let { BridgeStateManager.setBatteryLevel(it) }
                }
            }"""

new_battery = """            "BATTERY" -> {
                if (packet.args.isNotEmpty()) {
                    packet.args[0].toIntOrNull()?.let { BridgeStateManager.setBatteryLevel(it) }
                }
            }
            "GET_PHONE_BATTERY" -> {
                val batteryManager = service.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
                val batteryPct = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                service.sendPacket(BSBPacket("PHONE_BATTERY", listOf(batteryPct.toString())))
            }"""

content = content.replace(old_battery, new_battery)

with open('app/src/main/java/com/hamza/blackberrybridge/protocol/CommandDispatcher.kt', 'w') as f:
    f.write(content)
