import re

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'r') as f:
    content = f.read()

# Make the Connected Device card clickable to show the bottom sheet
content = content.replace('Text(if (isConnected) deviceName ?: "BlackBerry" else "No active link", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)', 
                          'Text(if (isConnected) deviceName ?: "BlackBerry" else "No active link", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)')

# Let's search for "if (isConnected) {" inside StatusContent to add clickable to the Row
pattern_connected = re.compile(r'Row\(\s*modifier = Modifier.fillMaxWidth\(\).padding\(bottom = 16.dp\),\s*horizontalArrangement = Arrangement.SpaceBetween,\s*verticalAlignment = Alignment.Top\s*\)')

replacement_connected = """var showControlPanel by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable(enabled = isConnected) { showControlPanel = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            )"""

content = content.replace("""Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            )""", replacement_connected)

# Now append the ModalBottomSheet definition at the end of StatusContent
sheet_ui = """
    if (showControlPanel && isConnected) {
        DeviceControlBottomSheet(
            deviceName = deviceName ?: "BlackBerry",
            onDismiss = { showControlPanel = false },
            context = context
        )
    }
"""

# inject at the end of the Box(modifier = Modifier.fillMaxSize()) in StatusContent
content = content.replace("""        if (!isConnected) {
            FloatingActionButton(""", sheet_ui + """
        if (!isConnected) {
            FloatingActionButton(""")

# We also need to add the Composable `DeviceControlBottomSheet`
bottom_sheet_composable = """
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlBottomSheet(deviceName: String, onDismiss: () -> Unit, context: android.content.Context) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dataStore = remember { com.hamza.blackberrybridge.settings.SettingsDataStore(context) }
    val scope = rememberCoroutineScope()
    
    val allowCalls by dataStore.allowCallsFlow.collectAsState(initial = true)
    val allowSms by dataStore.allowSmsFlow.collectAsState(initial = true)
    val allowNotif by dataStore.notificationForwardingFlow.collectAsState(initial = true)
    val allowMedia by dataStore.mediaControlFlow.collectAsState(initial = true)
    
    var masterSwitch by remember { mutableStateOf(allowCalls && allowSms && allowNotif && allowMedia) }
    val haptic = LocalHapticFeedback.current
    
    // Automatically update master switch when individual settings change
    LaunchedEffect(allowCalls, allowSms, allowNotif, allowMedia) {
        masterSwitch = allowCalls && allowSms && allowNotif && allowMedia
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgCard,
        contentColor = TextWhite
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.size(56.dp).background(BgInner, CircleShape).border(1.dp, BorderDark, CircleShape), contentAlignment = Alignment.Center) {
                    Text("📱", fontSize = 28.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(deviceName, color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Master Control Panel", color = TextMuted, fontSize = 14.sp)
                }
                Switch(
                    checked = masterSwitch,
                    onCheckedChange = { isChecked ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        masterSwitch = isChecked
                        scope.launch {
                            dataStore.setAllowCalls(isChecked)
                            dataStore.setAllowSms(isChecked)
                            dataStore.setNotificationForwarding(isChecked)
                            dataStore.setMediaControl(isChecked)
                            com.hamza.blackberrybridge.state.BridgeStateManager.logEvent(
                                if (isChecked) "Synchronisation globale activée" else "Mode Ne Pas Déranger activé",
                                if (isChecked) com.hamza.blackberrybridge.state.EventType.SUCCESS else com.hamza.blackberrybridge.state.EventType.WARNING
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentGreen, uncheckedThumbColor = Color.Gray, uncheckedTrackColor = BgInner)
                )
            }
            
            Divider(color = BorderDark, modifier = Modifier.padding(vertical = 8.dp))
            
            Text("Synchronization Channels", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            
            PermissionToggle("📞", "Phone Calls", "Allow answering/rejecting calls", allowCalls) { 
                scope.launch { dataStore.setAllowCalls(it) } 
            }
            PermissionToggle("💬", "Messages (SMS)", "Forward incoming text messages", allowSms) { 
                scope.launch { dataStore.setAllowSms(it) } 
            }
            PermissionToggle("🔔", "Notifications", "Forward app notifications", allowNotif) { 
                scope.launch { dataStore.setNotificationForwarding(it) } 
            }
            PermissionToggle("⏰", "Alarms & Media", "Sync alarms and playback controls", allowMedia) { 
                scope.launch { dataStore.setMediaControl(it) } 
            }
        }
    }
}

@Composable
fun PermissionToggle(icon: String, title: String, subtitle: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().background(BgInner, RoundedCornerShape(16.dp)).border(1.dp, BorderDark, RoundedCornerShape(16.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(icon, fontSize = 24.sp)
            Column {
                Text(title, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextMuted, fontSize = 12.sp)
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = { 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(it) 
            },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentGreen, uncheckedThumbColor = Color.Gray, uncheckedTrackColor = BgInner)
        )
    }
}
"""

if "fun DeviceControlBottomSheet" not in content:
    content = content + bottom_sheet_composable

if "import androidx.compose.material3.ModalBottomSheet" not in content:
    content = content.replace("import androidx.compose.material3.*", "import androidx.compose.material3.*\nimport androidx.compose.material3.ModalBottomSheet\nimport androidx.compose.material3.rememberModalBottomSheetState\nimport androidx.compose.material3.ExperimentalMaterial3Api")

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'w') as f:
    f.write(content)
