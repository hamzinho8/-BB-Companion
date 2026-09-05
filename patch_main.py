import re

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'r') as f:
    content = f.read()

# Define replacement function
new_status_content = """@Composable
fun StatusContent(context: android.content.Context) {
    val haptic = LocalHapticFeedback.current
    val isConnected by com.hamza.blackberrybridge.state.BridgeStateManager.isConnected.collectAsState()
    val deviceName by com.hamza.blackberrybridge.state.BridgeStateManager.deviceName.collectAsState()
    val discoveredDevices by com.hamza.blackberrybridge.state.BridgeStateManager.discoveredDevices.collectAsState()
    val batteryLevel by com.hamza.blackberrybridge.state.BridgeStateManager.batteryLevel.collectAsState()
    val isServiceRunning by com.hamza.blackberrybridge.state.BridgeStateManager.isServiceRunning.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(BgCard, RoundedCornerShape(16.dp)).border(1.dp, BorderDark, RoundedCornerShape(16.dp)).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Bridge Service", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (isServiceRunning) "Running in background" else "Stopped", color = TextMuted, fontSize = 12.sp)
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
            modifier = Modifier.fillMaxWidth().background(BgCard, RoundedCornerShape(24.dp)).border(1.dp, BorderDark, RoundedCornerShape(24.dp)).padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(if (isConnected) "Connected Device" else "Disconnected", color = TextMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (isConnected) deviceName ?: "BlackBerry" else "No active link", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (isConnected) {
                        Text("BBOS Companion", color = TextMuted, fontSize = 12.sp)
                    }
                }
                Box(
                    modifier = Modifier.background(BgButton, RoundedCornerShape(50)).border(1.dp, BorderLight, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(if (isConnected) "BT: SPP/CONNECTED" else "BT: DISCONNECTED", color = if (isConnected) AccentGreen else AccentRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
            
            if (isConnected) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("Battery", if (batteryLevel != null) "${batteryLevel}%" else "--", modifier = Modifier.weight(1f))
                    StatBox("Signal", "-62dB", modifier = Modifier.weight(1f))
                    StatBox("Weather", "22°C", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        BluetoothService.instance?.sendPacket(com.hamza.blackberrybridge.protocol.BSBPacket("FIND_PHONE", emptyList()))
                    },
                    modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = BgButton), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderLight)
                ) {
                    Text("🔍 Find my BlackBerry", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
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
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        Text("Scan for BlackBerry Devices", color = BgMain, fontWeight = FontWeight.Bold)
                    }
                    
                    if (discoveredDevices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            discoveredDevices.forEach { device ->
                                @SuppressLint("MissingPermission")
                                val dName = device.name ?: device.address
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(BgInner, RoundedCornerShape(12.dp)).border(1.dp, BorderDark, RoundedCornerShape(12.dp)).clickable {
                                        BluetoothService.instance?.let { svc ->
                                            svc.bluetoothManager?.connectToDevice(device, svc)
                                        }
                                    }.padding(16.dp)
                                ) {
                                    Text("📱 $dName", color = TextWhite, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}"""

# Use regex to replace the old StatusContent entirely
# It spans from "@Composable\nfun StatusContent(context: android.content.Context) {" to the next "@Composable\nfun LogsContent()"
pattern = re.compile(r'@Composable\s*\nfun StatusContent.*?^}', re.DOTALL | re.MULTILINE)
# We need a slightly more robust regex or just match up to "@Composable\nfun LogsContent()"
pattern = re.compile(r'@Composable\s*\nfun StatusContent.*?\}\s*\n\s*\n@Composable\s*\nfun LogsContent\(\)', re.DOTALL)

# Let's verify we found it
match = pattern.search(content)
if match:
    new_content = content[:match.start()] + new_status_content + "\n\n@Composable\nfun LogsContent()" + content[match.end():]
    with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'w') as f:
        f.write(new_content)
    print("Success")
else:
    print("Failed to match")
