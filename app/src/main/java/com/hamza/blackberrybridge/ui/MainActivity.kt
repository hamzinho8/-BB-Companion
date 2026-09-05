package com.hamza.blackberrybridge.ui

import android.annotation.SuppressLint
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.settings.BridgeSettings
import com.hamza.blackberrybridge.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // In a real app, handle permission denials gracefully.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        permissions.add(Manifest.permission.READ_PHONE_STATE)
        permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
        permissions.add(Manifest.permission.READ_CONTACTS)

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ungranted.isNotEmpty()) {
            requestPermissionLauncher.launch(ungranted.toTypedArray())
        }
    }
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { BridgeSettings(context) }
    var hasSeenOnboarding by remember { mutableStateOf(settings.hasSeenOnboarding) }

    if (hasSeenOnboarding) {
        MainScreen(modifier)
    } else {
        OnboardingScreen(modifier) {
            settings.hasSeenOnboarding = true
            hasSeenOnboarding = true
        }
    }
}

@Composable
fun OnboardingScreen(modifier: Modifier = Modifier, onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxSize().background(BgMain)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (page) {
                    0 -> {
                        Text("📡", fontSize = 80.sp, modifier = Modifier.padding(bottom = 32.dp))
                        Text("Bridge to the Past", color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Transform your BlackBerry Bold 9790 into a modern companion terminal.", color = TextMuted, textAlign = TextAlign.Center)
                    }
                    1 -> {
                        Text("🔋", fontSize = 80.sp, modifier = Modifier.padding(bottom = 32.dp))
                        Text("Smart Sync", color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Seamlessly sync notifications, calls, clipboard, and music via Bluetooth Classic.", color = TextMuted, textAlign = TextAlign.Center)
                    }
                    2 -> {
                        Text("🛡️", fontSize = 80.sp, modifier = Modifier.padding(bottom = 32.dp))
                        Text("Privacy First", color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("All data remains local between your phone and BlackBerry. We respect your digital boundaries.", color = TextMuted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (pagerState.currentPage == index) AccentGreen else BorderLight))
                }
            }
            
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (pagerState.currentPage < 2) {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onComplete()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Text(if (pagerState.currentPage == 2) "Start Bridge" else "Next", color = BgMain, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgMain)
    ) {
        // Header
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
        }

        Box(modifier = Modifier.weight(1f)) {
            when (currentTab) {
                0 -> StatusContent(context = context)
                1 -> LogsContent()
                2 -> SettingsContent(context = context)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgHeader)
                .border(width = 1.dp, color = BorderDark, shape = RectangleShape)
                .padding(vertical = 16.dp, horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem("📊", "Status", isActive = currentTab == 0) { 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentTab = 0 
            }
            NavItem("📁", "Logs", isActive = currentTab == 1) { 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentTab = 1 
            }
            NavItem("⚙️", "Config", isActive = currentTab == 2) { 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentTab = 2 
            }
        }
    }
}

@Composable
fun StatusContent(context: android.content.Context) {
    var showControlPanel by remember { mutableStateOf(false) }
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
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable(enabled = isConnected) { showControlPanel = true },
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

    if (showControlPanel && isConnected) {
        DeviceControlBottomSheet(
            deviceName = deviceName ?: "BlackBerry",
            onDismiss = { showControlPanel = false },
            context = context
        )
    }

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


@Composable
fun LogsContent() {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("System Logs", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(12.dp)).padding(12.dp).verticalScroll(rememberScrollState())
        ) {
            TerminalLine("[SYSTEM] Logging initialized...", TerminalText, 1f)
            TerminalLine("[SYSTEM] Connecting Bluetooth SPP...", TextMuted, 0.8f)
        }
    }
}

@Composable
fun SettingsContent(context: android.content.Context) {
    val dataStore = remember { com.hamza.blackberrybridge.settings.SettingsDataStore(context) }
    val scope = rememberCoroutineScope()
    
    val autoConnect by dataStore.autoConnectFlow.collectAsState(initial = true)
    val notifEnabled by dataStore.notificationForwardingFlow.collectAsState(initial = true)
    val mediaEnabled by dataStore.mediaControlFlow.collectAsState(initial = true)
    
    // Health checks
    val isNotifAllowed = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    val hasBtConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Health & Permissions", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        
        HealthItem("Notification Access", isNotifAllowed) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        HealthItem("Bluetooth Connect", hasBtConnect) {}
        HealthItem("Contacts Access", hasContacts) {}

        Text("Configuration", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        
        SettingsToggle("Auto-connect", "Automatically connect to BB", autoConnect) {
            scope.launch { dataStore.setAutoConnect(it) }
        }
        SettingsToggle("Notification forwarding", "Forward incoming messages", notifEnabled) {
            scope.launch { dataStore.setNotificationForwarding(it) }
        }
        SettingsToggle("Media control", "Control Spotify/Music", mediaEnabled) {
            scope.launch { dataStore.setMediaControl(it) }
        }
    }
}

@Composable
fun HealthItem(name: String, isOk: Boolean, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().background(BgInner, RoundedCornerShape(12.dp)).border(1.dp, BorderDark, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = TextWhite, fontSize = 14.sp)
        Box(modifier = Modifier.background(if (isOk) AccentGreen.copy(alpha = 0.2f) else AccentRed.copy(alpha = 0.2f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(if (isOk) "GRANTED" else "DENIED", color = if (isOk) AccentGreen else AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsToggle(title: String, subtitle: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().background(BgCard, RoundedCornerShape(16.dp)).border(1.dp, BorderDark, RoundedCornerShape(16.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onCheckedChange(it) },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentGreen, uncheckedThumbColor = Color.Gray, uncheckedTrackColor = BgInner)
        )
    }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(BgInner, RoundedCornerShape(16.dp)).border(1.dp, BorderDark, RoundedCornerShape(16.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 10.sp, letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 4.dp))
        Text(value, color = TextWhite, fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TerminalLine(text: String, color: Color, alpha: Float, isItalic: Boolean = false) {
    Text(text = text, color = color.copy(alpha = alpha), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal, modifier = Modifier.padding(bottom = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun ActionCard(icon: String, title: String, subtitle: String, iconBg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier = modifier.background(BgButton, RoundedCornerShape(16.dp)).border(1.dp, BorderLight, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(40.dp).background(iconBg, CircleShape), contentAlignment = Alignment.Center) { Text(icon, fontSize = 16.sp) }
        Column { Text(title, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = TextMuted, fontSize = 9.sp) }
    }
}

@Composable
fun NavItem(icon: String, label: String, isActive: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (isActive) 1f else 0.4f).clickable(onClick = onClick)) {
        if (isActive) { Box(modifier = Modifier.size(48.dp, 32.dp).background(BorderDark, RoundedCornerShape(50)).padding(bottom = 4.dp), contentAlignment = Alignment.Center) { Text(icon, fontSize = 16.sp) } } 
        else { Text(icon, fontSize = 20.sp, modifier = Modifier.padding(bottom = 4.dp)) }
        Text(label, color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

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
            
            HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 8.dp))
            
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
