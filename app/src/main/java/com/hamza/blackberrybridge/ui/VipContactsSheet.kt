package com.hamza.blackberrybridge.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamza.blackberrybridge.contacts.VipContact
import com.hamza.blackberrybridge.contacts.VipContactManager
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modern VIP Contact Dashboard Card
 */
@Composable
fun VipContactsCard(
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vipList by VipContactManager.vipContacts.collectAsState()
    val lastSync by VipContactManager.lastSyncTimestamp.collectAsState()
    val isConnected by BridgeStateManager.isConnected.collectAsState()

    var isSyncing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        VipContactManager.init(context)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(24.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF8E24AA), Color(0xFF5E35B1))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "VIP",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Contacts VIP (BlackBerry)",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Partage sélectif pour votre BlackBerry",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Count badge
            Box(
                modifier = Modifier
                    .background(
                        if (vipList.isNotEmpty()) Color(0xFF8E24AA).copy(alpha = 0.2f) else BgInner,
                        RoundedCornerShape(50)
                    )
                    .border(
                        1.dp,
                        if (vipList.isNotEmpty()) Color(0xFFBA68C8) else BorderLight,
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${vipList.size}/${VipContactManager.MAX_VIP_CONTACTS}",
                    color = if (vipList.isNotEmpty()) Color(0xFFE1BEE7) else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Subtitle Info
        Text(
            text = "Seuls ces contacts sélectionnés seront partagés avec votre BlackBerry (plutôt que l'ensemble de votre répertoire).",
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        // VIP Avatars Preview
        if (vipList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgInner)
                    .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
                    .clickable { onOpenPicker() }
                    .padding(vertical = 18.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Sélectionner mes 10 contacts importants",
                        color = AccentGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            // Horizontal scroll of VIP contacts chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                vipList.forEachIndexed { index, contact ->
                    VipMiniChip(
                        contact = contact,
                        onRemove = {
                            VipContactManager.removeVip(context, contact.id)
                        }
                    )
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Manage VIP button
            OutlinedButton(
                onClick = onOpenPicker,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("manage_vip_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextWhite
                ),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextWhite)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Modifier (${vipList.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            // Sync to BlackBerry button
            Button(
                onClick = {
                    if (!isConnected) {
                        Toast.makeText(context, "Connectez d'abord votre BlackBerry en Bluetooth", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSyncing = true
                    coroutineScope.launch {
                        val success = withContext(Dispatchers.IO) {
                            VipContactManager.syncVipContactsToBlackBerry(context)
                        }
                        isSyncing = false
                        if (success) {
                            Toast.makeText(context, "${vipList.size} contacts VIP synchronisés !", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Erreur lors de la synchronisation", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1.2f)
                    .height(44.dp)
                    .testTag("sync_vip_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) Color(0xFF00C853) else Color(0xFF37474F)
                ),
                enabled = !isSyncing
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Envoi...", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Synchroniser", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Last sync indicator
        if (lastSync != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Dernière synchro : $lastSync",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun VipMiniChip(
    contact: VipContact,
    onRemove: () -> Unit
) {
    val initial = contact.name.trim().take(1).uppercase()
    val avatarBrush = remember(contact.name) { getAvatarBrush(contact.name) }

    Surface(
        color = BgInner,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BorderDark),
        modifier = Modifier.widthIn(min = 120.dp, max = 150.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(avatarBrush),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial.ifEmpty { "?" },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    color = TextWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.number,
                    color = TextMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Supprimer",
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Full Screen / Dialog Picker to select VIP contacts from the phone
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipContactPickerModal(
    onDismiss: () -> Unit,
    onSyncNow: () -> Unit
) {
    val context = LocalContext.current
    val vipList by VipContactManager.vipContacts.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var phoneContacts by remember { mutableStateOf<List<VipContact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load phone contacts in background
    LaunchedEffect(searchQuery) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val list = VipContactManager.fetchPhoneContacts(context, searchQuery)
            withContext(Dispatchers.Main) {
                phoneContacts = list
                isLoading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgHeader,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderLight) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sélection des Contacts VIP",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choisissez jusqu'à 10 contacts pour le BlackBerry",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                // Counter pill
                val isLimitReached = vipList.size >= VipContactManager.MAX_VIP_CONTACTS
                val pillColor = if (isLimitReached) AccentAmber else AccentGreen

                Box(
                    modifier = Modifier
                        .background(pillColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .border(1.dp, pillColor, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${vipList.size} / ${VipContactManager.MAX_VIP_CONTACTS}",
                        color = pillColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher un contact...", color = TextMuted, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BgInner,
                    unfocusedContainerColor = BgInner,
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vip_search_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Currently selected VIP Chips Row
            if (vipList.isNotEmpty()) {
                Text(
                    text = "Contacts sélectionnés (${vipList.size}) :",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vipList.forEach { vip ->
                        InputChip(
                            selected = true,
                            onClick = {
                                VipContactManager.removeVip(context, vip.id)
                            },
                            label = { Text(vip.name, fontSize = 12.sp, maxLines = 1) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Retirer", modifier = Modifier.size(14.dp))
                            },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = Color(0xFF5E35B1),
                                selectedLabelColor = Color.White,
                                selectedTrailingIconColor = Color.White
                            ),
                            border = InputChipDefaults.inputChipBorder(
                                enabled = true,
                                selected = true,
                                borderColor = Color(0xFFBA68C8)
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Contact List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentGreen)
                    }
                } else if (phoneContacts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Aucun contact trouvé pour '$searchQuery'" else "Aucun contact disponible",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(phoneContacts, key = { it.id + it.number }) { contact ->
                            val isSelected = vipList.any { it.id == contact.id || it.number == contact.number }
                            ContactPickItem(
                                contact = contact,
                                isSelected = isSelected,
                                onToggle = {
                                    val success = VipContactManager.toggleVip(context, contact)
                                    if (!success) {
                                        Toast.makeText(
                                            context,
                                            "Limite de 10 contacts VIP atteinte. Désélectionnez-en un d'abord.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Actions
            Surface(
                color = BgHeader,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderLight),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                    ) {
                        Text("Fermer", fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            onSyncNow()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                            .testTag("save_and_sync_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Enregistrer & Envoyer",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactPickItem(
    contact: VipContact,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val initial = contact.name.trim().take(1).uppercase()
    val avatarBrush = remember(contact.name) { getAvatarBrush(contact.name) }
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFBA68C8) else BorderDark,
        animationSpec = tween(200),
        label = "border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF2A1B3D) else BgInner,
        animationSpec = tween(200),
        label = "bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Avatar Circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(avatarBrush),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial.ifEmpty { "?" },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Name and Phone
            Column {
                Text(
                    text = contact.name,
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.number,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Star / Check Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color(0xFF8E24AA) else BgCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Default.Star else Icons.Default.Add,
                contentDescription = null,
                tint = if (isSelected) Color(0xFFFFD54F) else TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Generates a pleasant linear gradient brush based on the contact name's hash.
 */
private fun getAvatarBrush(name: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFFE91E63), Color(0xFF880E4F)), // Pink
        listOf(Color(0xFF9C27B0), Color(0xFF4A148C)), // Purple
        listOf(Color(0xFF3F51B5), Color(0xFF1A237E)), // Indigo
        listOf(Color(0xFF009688), Color(0xFF004D40)), // Teal
        listOf(Color(0xFFFF5722), Color(0xFFBF360C)), // Deep Orange
        listOf(Color(0xFF0288D1), Color(0xFF01579B)), // Light Blue
        listOf(Color(0xFF43A047), Color(0xFF1B5E20))  // Green
    )
    val index = (name.hashCode() and 0x7FFFFFFF) % palettes.size
    return Brush.linearGradient(palettes[index])
}
