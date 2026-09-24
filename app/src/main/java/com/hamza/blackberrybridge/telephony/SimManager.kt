package com.hamza.blackberrybridge.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SimCardInfo(
    val slotIndex: Int,          // 0 = SIM 1, 1 = SIM 2
    val subscriptionId: Int,
    val displayName: String,     // e.g. "inwi", "Orange"
    val carrierName: String,
    val number: String? = null,
    val phoneAccountHandle: PhoneAccountHandle? = null
)

object SimManager {
    private const val TAG = "SimManager"
    private const val PREFS_NAME = "bb_sim_settings"
    private const val KEY_PREFERRED_SIM_SLOT = "preferred_sim_slot"
    private const val KEY_AUDIO_ROUTE = "call_audio_route"

    // Audio route options
    const val AUDIO_SPEAKERPHONE = "SPEAKERPHONE" // Haut-parleur mains-libres (recommandé)
    const val AUDIO_BLUETOOTH = "BLUETOOTH"       // Kit mains-libres Bluetooth / SCO
    const val AUDIO_EARPIECE = "EARPIECE"         // Écouteur standard smartphone

    // Slot settings: -2 = Auto/System, -1 = Prompt, 0 = SIM 1, 1 = SIM 2
    const val SLOT_SYSTEM_DEFAULT = -2
    const val SLOT_PROMPT = -1

    private val _availableSims = MutableStateFlow<List<SimCardInfo>>(emptyList())
    val availableSims: StateFlow<List<SimCardInfo>> = _availableSims.asStateFlow()

    private val _preferredSlot = MutableStateFlow(SLOT_SYSTEM_DEFAULT)
    val preferredSlot: StateFlow<Int> = _preferredSlot.asStateFlow()

    private val _audioRoute = MutableStateFlow(AUDIO_SPEAKERPHONE)
    val audioRoute: StateFlow<String> = _audioRoute.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            val prefs = getPrefs(context)
            _preferredSlot.value = prefs.getInt(KEY_PREFERRED_SIM_SLOT, SLOT_SYSTEM_DEFAULT)
            _audioRoute.value = prefs.getString(KEY_AUDIO_ROUTE, AUDIO_SPEAKERPHONE) ?: AUDIO_SPEAKERPHONE
            isInitialized = true
        }
        refreshSims(context)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setPreferredSlot(context: Context, slot: Int) {
        init(context)
        getPrefs(context).edit().putInt(KEY_PREFERRED_SIM_SLOT, slot).apply()
        _preferredSlot.value = slot
    }

    fun setAudioRoute(context: Context, route: String) {
        init(context)
        getPrefs(context).edit().putString(KEY_AUDIO_ROUTE, route).apply()
        _audioRoute.value = route
    }

    @SuppressLint("MissingPermission")
    fun refreshSims(context: Context): List<SimCardInfo> {
        val list = mutableListOf<SimCardInfo>()
        try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val phoneAccounts = try {
                telecomManager?.callCapablePhoneAccounts ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            if (subManager != null) {
                val subList: List<SubscriptionInfo>? = subManager.activeSubscriptionInfoList
                if (!subList.isNullOrEmpty()) {
                    for (sub in subList) {
                        val slot = sub.simSlotIndex
                        val subId = sub.subscriptionId
                        val dispName = sub.displayName?.toString() ?: "SIM ${slot + 1}"
                        val carrier = sub.carrierName?.toString() ?: dispName
                        val num = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try { subManager.getPhoneNumber(subId) } catch (e: Exception) { sub.number }
                        } else {
                            sub.number
                        }

                        // Match Telecom PhoneAccountHandle
                        val matchedHandle = phoneAccounts.firstOrNull { handle ->
                            handle.id.contains(subId.toString()) || handle.id.contains(slot.toString())
                        } ?: phoneAccounts.getOrNull(slot)

                        list.add(
                            SimCardInfo(
                                slotIndex = slot,
                                subscriptionId = subId,
                                displayName = dispName,
                                carrierName = carrier,
                                number = num,
                                phoneAccountHandle = matchedHandle
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying subscriptions", e)
        }

        // If no SIM detected or permission not yet available, fallback placeholders
        if (list.isEmpty()) {
            list.add(SimCardInfo(0, 1, "SIM 1", "Opérateur 1", null))
            list.add(SimCardInfo(1, 2, "SIM 2", "Opérateur 2", null))
        }

        _availableSims.value = list
        return list
    }

    fun getSimBySlot(context: Context, slot: Int): SimCardInfo? {
        val sims = refreshSims(context)
        return sims.firstOrNull { it.slotIndex == slot }
    }

    /**
     * Resolves which SIM to use for an outgoing call.
     * Takes into account:
     * 1. explicit requested slot (if user tapped SIM 1 or SIM 2 on BlackBerry)
     * 2. user preferred slot configured in Android settings
     * 3. fallback to SIM 1
     */
    fun resolveTargetSim(context: Context, requestedSlot: Int? = null): SimCardInfo? {
        val sims = refreshSims(context)
        if (sims.isEmpty()) return null

        if (requestedSlot != null && requestedSlot >= 0) {
            return sims.firstOrNull { it.slotIndex == requestedSlot } ?: sims.firstOrNull()
        }

        val pref = _preferredSlot.value
        return when {
            pref >= 0 -> sims.firstOrNull { it.slotIndex == pref } ?: sims.firstOrNull()
            else -> sims.firstOrNull()
        }
    }

    /**
     * Configures the audio routing for speaking and listening during the call
     */
    fun applyCallAudioRoute(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val route = _audioRoute.value

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                audioManager.mode = AudioManager.MODE_IN_CALL

                when (route) {
                    AUDIO_SPEAKERPHONE -> {
                        // Loudspeaker handsfree (optimal for talking/listening from desk or pocket)
                        audioManager.isSpeakerphoneOn = true
                        Log.d(TAG, "Audio routed to Loudspeaker (Speakerphone ON)")
                    }
                    AUDIO_BLUETOOTH -> {
                        // Attempt Bluetooth SCO audio link
                        try {
                            audioManager.isSpeakerphoneOn = false
                            audioManager.startBluetoothSco()
                            audioManager.isBluetoothScoOn = true
                            Log.d(TAG, "Audio routed to Bluetooth SCO")
                        } catch (e: Exception) {
                            Log.w(TAG, "Bluetooth SCO failed, falling back to speakerphone", e)
                            audioManager.isSpeakerphoneOn = true
                        }
                    }
                    AUDIO_EARPIECE -> {
                        audioManager.isSpeakerphoneOn = false
                        try {
                            if (audioManager.isBluetoothScoOn) {
                                audioManager.stopBluetoothSco()
                                audioManager.isBluetoothScoOn = false
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                        Log.d(TAG, "Audio routed to normal phone earpiece")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying audio route", e)
            }
        }, 1200)
    }

    /**
     * Toggles Speakerphone state directly during a call
     */
    fun toggleSpeakerphone(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val newState = !audioManager.isSpeakerphoneOn
        audioManager.isSpeakerphoneOn = newState
        return newState
    }
}
