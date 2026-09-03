package com.hamza.blackberrybridge.ui

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
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().background(BgCard, RoundedCornerShape(24.dp)).border(1.dp, BorderDark, RoundedCornerShape(24.dp)).padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text("Connected Device", color = TextMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("BlackBerry Bold 9790", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("BBOS 7.1 • 192.168.1.15", color = TextMuted, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier.background(BgButton, RoundedCornerShape(50)).border(1.dp, BorderLight, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("BT: SPP/CONNECTED", color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Battery", "85%", modifier = Modifier.weight(1f))
                StatBox("Signal", "-62dB", modifier = Modifier.weight(1f))
                StatBox("Weather", "22°C", modifier = Modifier.weight(1f))
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(BgHeader, RoundedCornerShape(24.dp)).border(1.dp, BorderDark, RoundedCornerShape(24.dp)).padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PROTOCOL STREAM (BSB/1)", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AccentAmber))
            }
            
            Column(
                modifier = Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(12.dp)).padding(12.dp)
            ) {
                TerminalLine("[09:42:12] HELLO|BSB/1|ANDROID_PIXEL_7", TerminalText, 0.5f)
                TerminalLine("[09:42:15] PHONE_BATTERY|85", TextWhite, 1f)
                TerminalLine("[09:44:22] MEDIA_META|Midnight City", TerminalText, 1f)
                TerminalLine("[09:45:01] NOTIFICATION|721|WhatsApp", AccentRed, 1f)
                TerminalLine("[09:45:05] Waiting for remote command...", TerminalText, 0.5f, isItalic = true)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard(icon = "🛡️", title = "Service", subtitle = "Start BT", iconBg = IconOrange, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val intent = Intent(context, BluetoothService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
        
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val intent = Intent(context, BluetoothService::class.java)
                context.stopService(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BgButton),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderLight),
            contentPadding = PaddingValues(16.dp)
        ) {
            Text("Stop Service", color = AccentRed, fontWeight = FontWeight.Bold)
        }
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
    val settings = remember { BridgeSettings(context) }
    
    var notifEnabled by remember { mutableStateOf(settings.isNotificationsEnabled) }
    var callsEnabled by remember { mutableStateOf(settings.isCallsEnabled) }
    var mediaEnabled by remember { mutableStateOf(settings.isMediaEnabled) }
    
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
        
        SettingsToggle("Enable Notifications", "Forward incoming messages", notifEnabled) { 
            notifEnabled = it; settings.isNotificationsEnabled = it 
        }
        SettingsToggle("Enable Call Control", "Answer/Reject from BB", callsEnabled) { 
            callsEnabled = it; settings.isCallsEnabled = it 
        }
        SettingsToggle("Enable Media Sync", "Control Spotify/Music", mediaEnabled) { 
            mediaEnabled = it; settings.isMediaEnabled = it 
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
