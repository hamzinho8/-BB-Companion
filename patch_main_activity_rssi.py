with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'r') as f:
    content = f.read()

# Add rssiLevel collect
if 'val rssiLevel by ' not in content:
    content = content.replace(
        'val batteryLevel by com.hamza.blackberrybridge.state.BridgeStateManager.batteryLevel.collectAsState()',
        'val batteryLevel by com.hamza.blackberrybridge.state.BridgeStateManager.batteryLevel.collectAsState()\n    val rssiLevel by com.hamza.blackberrybridge.state.BridgeStateManager.rssiLevel.collectAsState()'
    )

# Find where StatBox("Batterie") is and replace the Sync one.
old_row = """                    if (isConnected) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox("Batterie", if (batteryLevel != null) "${batteryLevel}%" else "--", modifier = Modifier.weight(1f))
                            StatBox("Sync.", "Actif", modifier = Modifier.weight(1f))
                        }"""

new_row = """                    if (isConnected) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox("Batterie", if (batteryLevel != null) "${batteryLevel}%" else "--", modifier = Modifier.weight(1f))
                            SignalStrengthBox(rssiLevel, modifier = Modifier.weight(1f))
                        }"""

content = content.replace(old_row, new_row)

# Add SignalStrengthBox composable
signal_box_code = """
@Composable
fun SignalStrengthBox(rssi: Int?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(BgInner, RoundedCornerShape(12.dp)).border(1.dp, BorderDark, RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Signal", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(24.dp)) {
            val bars = when {
                rssi == null -> 0
                rssi >= -60 -> 4
                rssi >= -70 -> 3
                rssi >= -80 -> 2
                else -> 1
            }
            
            for (i in 1..4) {
                val isActive = i <= bars
                val color = if (isActive) AccentGreen else BorderLight
                val barHeight = (i * 6).dp
                Box(modifier = Modifier.width(6.dp).height(barHeight).background(color, RoundedCornerShape(2.dp)))
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (rssi != null) "${rssi} dBm" else "--",
                color = if (rssi != null) TextWhite else TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}
"""

content = content + signal_box_code

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'w') as f:
    f.write(content)
