with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'r') as f:
    content = f.read()

# Limit home screen to 5
content = content.replace('recentEvents.forEach { event ->', 'recentEvents.take(5).forEach { event ->')

# Replace LogsContent
old_logs = """fun LogsContent() {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Journaux Système", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(12.dp)).padding(12.dp).verticalScroll(rememberScrollState())
        ) {
            TerminalLine("[SYSTÈME] Journaux initialisés...", TerminalText, 1f)
            TerminalLine("[SYSTÈME] Connexion Bluetooth SPP...", TextMuted, 0.8f)
        }
    }
}"""

new_logs = """fun LogsContent() {
    val events by com.hamza.blackberrybridge.state.BridgeStateManager.recentEvents.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Journaux Système", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(12.dp)).padding(12.dp).verticalScroll(rememberScrollState())
        ) {
            if (events.isEmpty()) {
                TerminalLine("[SYSTÈME] En attente d'événements...", TextMuted, 0.8f)
            } else {
                events.forEach { event ->
                    val color = when(event.type) {
                        com.hamza.blackberrybridge.state.EventType.SUCCESS -> AccentGreen
                        com.hamza.blackberrybridge.state.EventType.WARNING -> AccentAmber
                        com.hamza.blackberrybridge.state.EventType.ERROR -> AccentRed
                        else -> TextWhite
                    }
                    val typeStr = event.type.name
                    TerminalLine("[${event.time}] [$typeStr] ${event.description}", color, 1f)
                }
            }
        }
    }
}"""

content = content.replace(old_logs, new_logs)

# Update TerminalLine to not have maxLines = 1 so long logs can be read
old_terminal_line = "modifier = Modifier.padding(bottom = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)"
new_terminal_line = "modifier = Modifier.padding(bottom = 4.dp))"
content = content.replace(old_terminal_line, new_terminal_line)

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'w') as f:
    f.write(content)
