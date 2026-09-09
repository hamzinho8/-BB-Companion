with open('app/src/main/java/com/hamza/blackberrybridge/battery/BatteryBridgeManager.kt', 'r') as f:
    content = f.read()

old_battery = """                // Avoid spamming Bluetooth. Only report significant changes.
                if (lastReportedLevel == -1 || Math.abs(currentPct - lastReportedLevel) >= threshold) {
                    lastReportedLevel = currentPct
                    service.sendPacket(BSBPacket("PHONE_BATTERY", listOf(currentPct.toString())))
                    Log.d(TAG, "Sent battery update: $currentPct%")
                }"""

new_battery = """                // Avoid spamming Bluetooth. Only report significant changes.
                if (lastReportedLevel == -1 || Math.abs(currentPct - lastReportedLevel) >= threshold) {
                    lastReportedLevel = currentPct
                    com.hamza.blackberrybridge.state.BridgeStateManager.logEvent("Niveau de batterie (Android): $currentPct%", com.hamza.blackberrybridge.state.EventType.INFO)
                    service.sendPacket(BSBPacket("PHONE_BATTERY", listOf(currentPct.toString())))
                    Log.d(TAG, "Sent battery update: $currentPct%")
                }"""
                
content = content.replace(old_battery, new_battery)

with open('app/src/main/java/com/hamza/blackberrybridge/battery/BatteryBridgeManager.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/hamza/blackberrybridge/weather/WeatherManager.kt', 'r') as f:
    content2 = f.read()

old_weather = """                    val condition = getWeatherCondition(weatherCode)
                    
                    // FORMAT: WEATHER|22|C|Soleil|Casablanca
                    service.sendPacket(BSBPacket("WEATHER", listOf(temp.toString(), "C", condition, "Casablanca")))"""

new_weather = """                    val condition = getWeatherCondition(weatherCode)
                    
                    com.hamza.blackberrybridge.state.BridgeStateManager.logEvent("Météo récupérée: $temp°C, $condition", com.hamza.blackberrybridge.state.EventType.INFO)
                    // FORMAT: WEATHER|22|C|Soleil|Casablanca
                    service.sendPacket(BSBPacket("WEATHER", listOf(temp.toString(), "C", condition, "Casablanca")))"""

content2 = content2.replace(old_weather, new_weather)

with open('app/src/main/java/com/hamza/blackberrybridge/weather/WeatherManager.kt', 'w') as f:
    f.write(content2)
