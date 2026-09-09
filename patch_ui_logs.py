with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'r') as f:
    content = f.read()

# Fix color mapping in Dashboard
old_dash = """                                val color = when(event.type) {
                                    com.hamza.blackberrybridge.state.EventType.SUCCESS -> AccentGreen
                                    com.hamza.blackberrybridge.state.EventType.WARNING -> AccentAmber
                                    com.hamza.blackberrybridge.state.EventType.ERROR -> AccentRed
                                    else -> TextWhite
                                }"""
new_dash = """                                val color = when(event.type) {
                                    com.hamza.blackberrybridge.state.EventType.SUCCESS -> AccentGreen
                                    com.hamza.blackberrybridge.state.EventType.WARNING -> AccentAmber
                                    com.hamza.blackberrybridge.state.EventType.ERROR -> AccentRed
                                    com.hamza.blackberrybridge.state.EventType.TX -> Color(0xFF4FC3F7)
                                    com.hamza.blackberrybridge.state.EventType.RX -> Color(0xFFCE93D8)
                                    else -> TextWhite
                                }"""
content = content.replace(old_dash, new_dash)

# Fix color mapping and formatting in Logs terminal
old_logs = """                events.forEach { event ->
                    val color = when(event.type) {
                        com.hamza.blackberrybridge.state.EventType.SUCCESS -> AccentGreen
                        com.hamza.blackberrybridge.state.EventType.WARNING -> AccentAmber
                        com.hamza.blackberrybridge.state.EventType.ERROR -> AccentRed
                        else -> TextWhite
                    }
                    val typeStr = event.type.name
                    TerminalLine("[${event.time}] [$typeStr] ${event.description}", color, 1f)
                }"""

new_logs = """                events.forEach { event ->
                    val color = when(event.type) {
                        com.hamza.blackberrybridge.state.EventType.SUCCESS -> AccentGreen
                        com.hamza.blackberrybridge.state.EventType.WARNING -> AccentAmber
                        com.hamza.blackberrybridge.state.EventType.ERROR -> AccentRed
                        com.hamza.blackberrybridge.state.EventType.TX -> Color(0xFF4FC3F7)
                        com.hamza.blackberrybridge.state.EventType.RX -> Color(0xFFCE93D8)
                        else -> TextWhite
                    }
                    val prefix = when(event.type) {
                        com.hamza.blackberrybridge.state.EventType.TX -> "→ [TX]"
                        com.hamza.blackberrybridge.state.EventType.RX -> "← [RX]"
                        else -> "[${event.type.name}]"
                    }
                    TerminalLine("[${event.time}] $prefix ${event.description}", color, 1f)
                }"""
content = content.replace(old_logs, new_logs)

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'w') as f:
    f.write(content)
