package com.hamza.blackberrybridge.ui

import androidx.compose.animation.AnimatedVisibility
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

    var showSimOptions by remember { mutableStateOf(false) }

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

                IconButton(onClick = { SimManager.refreshSims(context) }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualiser SIM",
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

            Spacer(modifier = Modifier.height(12.dp))

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
                    onClick = { SimManager.setAudioRoute(context, SimManager.AUDIO_SPEAKERPHONE) },
                    label = { Text("📢 Haut-parleur", fontSize = 12.sp) },
                    leadingIcon = if (audioRoute == SimManager.AUDIO_SPEAKERPHONE) {
                        { Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )

                // Option 2: Bluetooth SCO
                FilterChip(
                    selected = audioRoute == SimManager.AUDIO_BLUETOOTH,
                    onClick = { SimManager.setAudioRoute(context, SimManager.AUDIO_BLUETOOTH) },
                    label = { Text("🎧 Bluetooth", fontSize = 12.sp) },
                    leadingIcon = if (audioRoute == SimManager.AUDIO_BLUETOOTH) {
                        { Icon(Icons.Default.BluetoothAudio, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )

                // Option 3: Phone Earpiece
                FilterChip(
                    selected = audioRoute == SimManager.AUDIO_EARPIECE,
                    onClick = { SimManager.setAudioRoute(context, SimManager.AUDIO_EARPIECE) },
                    label = { Text("📱 Écouteur", fontSize = 12.sp) },
                    leadingIcon = if (audioRoute == SimManager.AUDIO_EARPIECE) {
                        { Icon(Icons.Default.PhoneInTalk, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when (audioRoute) {
                    SimManager.AUDIO_SPEAKERPHONE -> "Le haut-parleur s'active automatiquement pour parler et écouter mains-libres lors des appels BlackBerry."
                    SimManager.AUDIO_BLUETOOTH -> "Tente de router l'audio du microphone et de l'écouteur vers l'appareil Bluetooth connecté (HFP/SCO)."
                    else -> "L'audio passe par l'écouteur standard du smartphone Android."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}
