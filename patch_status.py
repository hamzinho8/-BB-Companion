import re

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'r') as f:
    content = f.read()

new_status_content = """@Composable
fun StatusContent(context: android.content.Context) {
    val haptic = LocalHapticFeedback.current
    val isConnected by com.hamza.blackberrybridge.state.BridgeStateManager.isConnected.collectAsState()
    val deviceName by com.hamza.blackberrybridge.state.BridgeStateManager.deviceName.collectAsState()
    val discoveredDevices by com.hamza.blackberrybridge.state.BridgeStateManager.discoveredDevices.collectAsState()
    val batteryLevel by com.hamza.blackberrybridge.state.BridgeStateManager.batteryLevel.collectAsState()
    val isServiceRunning by com.hamza.blackberrybridge.state.BridgeStateManager.isServiceRunning.collectAsState()
    val recentEvents by com.hamza.blackberrybridge.state.BridgeStateManager.recentEvents.collectAsState()

    var isScanning by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp).padding(bottom = 72.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(BgCard, RoundedCornerShape(16.dp)).border(1.dp, BorderDark, RoundedCornerShape(16.dp)).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bridge Service", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(if (isServiceRunning) "Active in background" else "Stopped", color = TextMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { isChecked ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val intent = Intent(context, BluetoothService::class.java)
                        if (isChecked) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { context.startForegroundService(intent) } else { context.startService(intent) }
                        } else {
                            context.stopService(intent)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentGreen, uncheckedThumbColor = Color.Gray, uncheckedTrackColor = BgInner)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(BgCard, RoundedCornerShape(24.dp)).border(1.dp, BorderDark, RoundedCornerShape(24.dp))
            ) {
                // Hero section
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp).background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(BgHeader, BgCard)
                        ),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing animation if scanning could go here, or just a cool icon
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(android.R.drawable.stat_sys_data_bluetooth),
                        contentDescription = "Bluetooth",
                        modifier = Modifier.size(48.dp),
                        tint = if (isConnected) AccentGreen else (if (isServiceRunning) AccentAmber else TextMuted)
                    )
                }

                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(if (isConnected) "Connected Device" else "Disconnected", color = TextMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (isConnected) deviceName ?: "BlackBerry" else "No active link", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier.background(BgButton, RoundedCornerShape(50)).border(1.dp, BorderLight, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(if (isConnected) "SPP/CONNECTED" else "DISCONNECTED", color = if (isConnected) AccentGreen else AccentRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    
                    if (isConnected) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox("Battery", if (batteryLevel != null) "${batteryLevel}%" else "--", modifier = Modifier.weight(1f))
                            StatBox("Sync", "Active", modifier = Modifier.weight(1f))
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Quick Actions", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            QuickActionBtn("Find", "🔍") { BluetoothService.instance?.sendPacket(com.hamza.blackberrybridge.protocol.BSBPacket("FIND_PHONE", emptyList())) }
                            QuickActionBtn("Lock", "🔒") { BluetoothService.instance?.sendPacket(com.hamza.blackberrybridge.protocol.BSBPacket("LOCK_PHONE", emptyList())) }
                            QuickActionBtn("Camera", "📸") { BluetoothService.instance?.sendPacket(com.hamza.blackberrybridge.protocol.BSBPacket("OPEN_CAMERA", emptyList())) }
                            QuickActionBtn("Media", "🎵") { BluetoothService.instance?.sendPacket(com.hamza.blackberrybridge.protocol.BSBPacket("MEDIA_PLAY_PAUSE", emptyList())) }
                        }
                    } else {
                        if (discoveredDevices.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Available Devices:", color = TextMuted, fontSize = 12.sp)
                                discoveredDevices.forEach { device ->
                                    @SuppressLint("MissingPermission")
                                    val dName = device.name ?: device.address
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(BgInner, RoundedCornerShape(12.dp)).border(1.dp, BorderDark, RoundedCornerShape(12.dp)).clickable {
                                            BluetoothService.instance?.let { svc ->
                                                svc.bluetoothManager?.connectToDevice(device, svc)
                                            }
                                        }.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📱", modifier = Modifier.padding(end = 12.dp))
                                        Text(dName, color = TextWhite, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Events
            Column(
                modifier = Modifier.fillMaxWidth().background(BgCard, RoundedCornerShape(24.dp)).border(1.dp, BorderDark, RoundedCornerShape(24.dp)).padding(20.dp)
            ) {
                Text("Recent Activity", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                if (recentEvents.isEmpty()) {
                    Text("No recent activity.", color = TextMuted, fontSize = 12.sp, fontStyle = FontStyle.Italic)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentEvents.forEach { event ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(event.time, color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(40.dp))
                                val color = when(event.type) {
                                    com.hamza.blackberrybridge.state.EventType.SUCCESS -> AccentGreen
                                    com.hamza.blackberrybridge.state.EventType.WARNING -> AccentAmber
                                    com.hamza.blackberrybridge.state.EventType.ERROR -> AccentRed
                                    else -> TextWhite
                                }
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(event.description, color = TextWhite, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // FAB for Scanning
        if (!isConnected) {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (BluetoothService.instance == null) {
                        val intent = Intent(context, BluetoothService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        BluetoothService.instance?.bluetoothManager?.startScanning()
                    }, 500)
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).padding(bottom = 60.dp),
                containerColor = AccentGreen,
                contentColor = BgMain
            ) {
                Text("🔍", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun QuickActionBtn(label: String, icon: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(8.dp)) {
        Box(modifier = Modifier.size(48.dp).background(BgButton, CircleShape).border(1.dp, BorderDark, CircleShape), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 20.sp)
        }
        Text(label, color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
"""

pattern = re.compile(r'@Composable\s*\nfun StatusContent.*?\}\s*\n\s*\n@Composable\s*\nfun LogsContent\(\)', re.DOTALL)
match = pattern.search(content)
if match:
    new_content = content[:match.start()] + new_status_content + "\n\n@Composable\nfun LogsContent()" + content[match.end():]
    with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'w') as f:
        f.write(new_content)
    print("Success")
else:
    print("Failed to match StatusContent")
