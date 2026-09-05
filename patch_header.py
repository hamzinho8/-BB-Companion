with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'r') as f:
    content = f.read()

target = """        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgHeader)
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .border(width = 1.dp, color = BorderDark, shape = RectangleShape),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("SYSTEM BRIDGE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("BB-Link Android", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
            
            // Pulse Indicator
            val infiniteTransition = rememberInfiniteTransition()
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f, targetValue = 0.8f,
                animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse)
            )
            
            Box(contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(AccentGreen.copy(alpha = alpha)))
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(AccentGreen).border(2.dp, BgMain, CircleShape))
            }
        }"""

replacement = """        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgMain)
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Logo Icon Box with Bluetooth Blue
                val btBlue = Color(0xFF007BFF)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(btBlue.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                        .border(1.dp, btBlue.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        painter = androidx.compose.ui.res.painterResource(android.R.drawable.stat_sys_data_bluetooth),
                        contentDescription = "Logo",
                        tint = btBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column {
                    Text("SYSTÈME BRIDGE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("BB Compagnon", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            
            // Status Indicator
            val infiniteTransition = rememberInfiniteTransition()
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.15f, targetValue = 0.6f,
                animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Reverse)
            )
            
            Box(contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(AccentGreen.copy(alpha = alpha)))
                Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(AccentGreen))
            }
        }"""

if target in content:
    content = content.replace(target, replacement)
    with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'w') as f:
        f.write(content)
    print("Header replaced successfully.")
else:
    print("Could not find the exact header pattern.")
