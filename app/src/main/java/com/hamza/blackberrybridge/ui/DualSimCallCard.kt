package com.hamza.blackberrybridge.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamza.blackberrybridge.telephony.SimManager
import com.hamza.blackberrybridge.telephony.SimCardInfo

@Composable
fun DualSimCallCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        SimManager.init(context)
    }

    val availableSims by SimManager.availableSims.collectAsState()
    val preferredSlot by SimManager.preferredSlot.collectAsState()
    val audioRoute by SimManager.audioRoute.collectAsState()
    val isBtAudioConnected by SimManager.isBluetoothAudioConnected.collectAsState()
    val btDeviceName by SimManager.connectedAudioDeviceName.collectAsState()
    val isBridgeActive by com.hamza.blackberrybridge.audio.CallAudioBridge.isBridgeActive.collectAsState()
    val isVoipEnabled by com.hamza.blackberrybridge.audio.CallAudioBridge.isVoipEnabled.collectAsState()
    val txPackets by com.hamza.blackberrybridge.audio.CallAudioBridge.txPackets.collectAsState()
    val rxPackets by com.hamza.blackberrybridge.audio.CallAudioBridge.rxPackets.collectAsState()
    val isDigitalCapture by com.hamza.blackberrybridge.audio.CallAudioBridge.isDigitalCapture.collectAsState()
    val isSpeakerMuted by com.hamza.blackberrybridge.audio.CallAudioBridge.isSpeakerMuted.collectAsState()
    val audioLevel by com.hamza.blackberrybridge.audio.CallAudioBridge.audioLevel.collectAsState()
    val hasAudioSignal by com.hamza.blackberrybridge.audio.CallAudioBridge.hasAudioSignal.collectAsState()
    val captureStatus by com.hamza.blackberrybridge.audio.CallAudioBridge.captureStatus.collectAsState()
    val totalBytesSent by com.hamza.blackberrybridge.audio.CallAudioBridge.totalBytesSent.collectAsState()
    val currentSampleRate by com.hamza.blackberrybridge.audio.CallAudioBridge.sampleRate.collectAsState()

    var testSpeakerFeedback by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Appels & SIM",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Gestion Double SIM & Appels",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (availableSims.size > 1) "${availableSims.size} cartes SIM détectées" else "1 carte SIM active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = {
                    SimManager.refreshSims(context)
                    SimManager.checkBluetoothAudioDevices(context)
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualiser SIM & Audio",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SIM Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableSims.forEach { sim ->
                    val isSelected = (preferredSlot == sim.slotIndex) || 
                                     (preferredSlot == SimManager.SLOT_SYSTEM_DEFAULT && sim.slotIndex == 0)

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                SimManager.setPreferredSlot(context, sim.slotIndex)
                            },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        tonalElevation = if (isSelected) 4.dp else 1.dp,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "SIM ${sim.slotIndex + 1}: ${sim.displayName}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (isSelected) "Puce par défaut (BB)" else "Cliquer pour choisir",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Audio routing choice
            Text(
                text = "Mode Audio (Parler & Écouter) :",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Option 1: Speakerphone (recommended)
                FilterChip(
                    selected = audioRoute == SimManager.AUDIO_SPEAKERPHONE,
                    onClick = {
                        SimManager.setAudioRoute(context, SimManager.AUDIO_SPEAKERPHONE)
                        SimManager.applyCallAudioRoute(context)
                    },
                    label = { Text("📢 Haut-parleur", fontSize = 12.sp) },
                    leadingIcon = if (audioRoute == SimManager.AUDIO_SPEAKERPHONE) {
                        { Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )

                // Option 2: Bluetooth SCO
                FilterChip(
                    selected = audioRoute == SimManager.AUDIO_BLUETOOTH,
                    onClick = {
                        SimManager.setAudioRoute(context, SimManager.AUDIO_BLUETOOTH)
                        SimManager.checkBluetoothAudioDevices(context)
                        SimManager.applyCallAudioRoute(context)
                    },
                    label = { Text("🎧 Bluetooth", fontSize = 12.sp) },
                    leadingIcon = if (audioRoute == SimManager.AUDIO_BLUETOOTH) {
                        { Icon(Icons.Default.BluetoothAudio, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )

                // Option 3: Phone Earpiece
                FilterChip(
                    selected = audioRoute == SimManager.AUDIO_EARPIECE,
                    onClick = {
                        SimManager.setAudioRoute(context, SimManager.AUDIO_EARPIECE)
                        SimManager.applyCallAudioRoute(context)
                    },
                    label = { Text("📱 Écouteur", fontSize = 12.sp) },
                    leadingIcon = if (audioRoute == SimManager.AUDIO_EARPIECE) {
                        { Icon(Icons.Default.PhoneInTalk, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dynamic route details card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = when (audioRoute) {
                            SimManager.AUDIO_SPEAKERPHONE -> "📢 Haut-parleur Mains-libres renforcé : s'enclenche avec impulsions matérielles automatiques dès le lancement ou le décrochage de l'appel pour parler et écouter sans toucher le smartphone."
                            SimManager.AUDIO_BLUETOOTH -> "🎧 Canal Audio Bluetooth SCO : dirige le son des appels vers l'appareil audio Bluetooth appairé (BlackBerry avec profil HFP ou oreillette/AirPods)."
                            else -> "📱 Écouteur standard du smartphone Android."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (audioRoute == SimManager.AUDIO_BLUETOOTH) {
                        Spacer(modifier = Modifier.height(6.dp))
                        if (isBtAudioConnected) {
                            Text(
                                text = "✅ Périphérique audio détecté : ${btDeviceName ?: "Audio Bluetooth actif"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF2E7D32)
                            )
                        } else {
                            Text(
                                text = "ℹ️ Aucun canal audio d'appel Bluetooth actif. Pour parler via le BlackBerry, vérifiez dans Paramètres Bluetooth Android > BlackBerry > activez « Audio des appels ».",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // VoIP Streaming Switch & Live Status Badge
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isVoipEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🎧 Relais Audio Appels vers BlackBerry",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isVoipEnabled) 
                                    "Actif : la voix de l'appel est transmise au combiné BlackBerry Curve 9300"
                                else 
                                    "Mode SmartWatch : diffusion directe sur haut-parleur smartphone",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isVoipEnabled,
                            onCheckedChange = { checked ->
                                com.hamza.blackberrybridge.audio.CallAudioBridge.setVoipEnabled(checked)
                                if (checked && !isBridgeActive) {
                                    val main = MainActivity.instance
                                    if (main != null) {
                                        main.requestMediaProjectionAndStartAudio()
                                    } else {
                                        com.hamza.blackberrybridge.bluetooth.BluetoothService.instance?.let {
                                            com.hamza.blackberrybridge.audio.CallAudioBridge.startStreaming(it)
                                        }
                                    }
                                }
                            }
                        )
                    }

                    // Visual Audio Stream Monitor
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBridgeActive) {
                                if (hasAudioSignal) Color(0xFF1B5E20).copy(alpha = 0.12f)
                                else Color(0xFFE65100).copy(alpha = 0.10f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            // Header with Status Badge and Animated Equalizer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isBridgeActive) {
                                                    if (hasAudioSignal) Color(0xFF2E7D32) else Color(0xFFF57C00)
                                                } else Color.Gray
                                            )
                                    )
                                    Text(
                                        text = if (isBridgeActive) {
                                            if (hasAudioSignal) "Signal audio détecté (Pur Numérique)"
                                            else "Capture active - En attente de son"
                                        } else "Diffusion arrêtée",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isBridgeActive) {
                                            if (hasAudioSignal) Color(0xFF2E7D32) else Color(0xFFE65100)
                                        } else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // 5 Animated Equalizer Bars
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.height(20.dp)
                                ) {
                                    val barMultipliers = listOf(0.6f, 1.0f, 0.75f, 1.2f, 0.85f)
                                    barMultipliers.forEachIndexed { idx, mult ->
                                        val targetHeight = if (isBridgeActive) {
                                            if (hasAudioSignal) (6.dp + 14.dp * (audioLevel * mult).coerceIn(0.15f, 1.0f))
                                            else 4.dp
                                        } else 3.dp

                                        val animatedH by animateDpAsState(
                                            targetValue = targetHeight,
                                            animationSpec = spring(stiffness = Spring.StiffnessHigh),
                                            label = "bar_$idx"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(animatedH)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(
                                                    if (isBridgeActive) {
                                                        if (hasAudioSignal) Color(0xFF2E7D32) else Color(0xFFF57C00)
                                                    } else Color.Gray.copy(alpha = 0.4f)
                                                )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Dynamic Status Message
                            Text(
                                text = if (isBridgeActive) {
                                    if (hasAudioSignal) "🎵 Son en cours de transmission vers BlackBerry (${(audioLevel * 100).toInt()}% puissance)"
                                    else "⏸️ Aucun son détecté : lancez une vidéo YouTube ou de la musique sur le smartphone"
                                } else "Prêt à diffuser le son numérique vers votre BlackBerry Curve 9300",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isBridgeActive || txPackets > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(6.dp))

                                // Metrics Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "📤 $txPackets blocs (~${txPackets / 5}s direct)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "📊 ${totalBytesSent / 1024} Ko transmis",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "🎛️ 16kHz HD (DSP 120Hz)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            // DSP Audio Configuration Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Filtres DSP :",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text("🛡️ HPF 120Hz + Limiteur -3dB", fontSize = 10.sp) }
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = {
                                    MainActivity.instance?.requestBatteryOptimizationExemption()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "⚡ MIUI : Pas de restriction batterie (Arrière-plan)",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (isBridgeActive) {
                                com.hamza.blackberrybridge.audio.CallAudioBridge.stopStreaming()
                            } else {
                                val main = MainActivity.instance
                                if (main != null) {
                                    main.requestMediaProjectionAndStartAudio()
                                } else {
                                    com.hamza.blackberrybridge.bluetooth.BluetoothService.instance?.let {
                                        com.hamza.blackberrybridge.audio.CallAudioBridge.startStreaming(it)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBridgeActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isBridgeActive) Icons.Default.VolumeOff else Icons.Default.Headset,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBridgeActive) "Arrêter la diffusion audio" else "🎧 Diffuser le son (YouTube, Musique, Appels)",
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                com.hamza.blackberrybridge.audio.CallAudioBridge.toggleSpeakerMute(context)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isSpeakerMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSpeakerMuted) "HP : Réduit (1)" else "HP : Normal",
                                fontSize = 11.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                com.hamza.blackberrybridge.bluetooth.BluetoothService.instance?.let { s ->
                                    com.hamza.blackberrybridge.audio.CallAudioBridge.sendTestBeep(s)
                                }
                            },
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "🔔 Tester son BlackBerry",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Direct Test & Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val isOn = SimManager.toggleSpeakerphone(context)
                        testSpeakerFeedback = if (isOn) "Haut-parleur activé !" else "Haut-parleur désactivé"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (audioRoute == SimManager.AUDIO_SPEAKERPHONE) "Tester / Couper HP" else "Activer Haut-parleur",
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // ignore
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsBluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bluetooth Android", fontSize = 12.sp)
                }
            }

            testSpeakerFeedback?.let { msg ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
