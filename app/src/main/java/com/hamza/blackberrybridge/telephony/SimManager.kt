package com.hamza.blackberrybridge.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.calls.BridgeInCallService
import com.hamza.blackberrybridge.protocol.BSBPacket
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.state.EventType
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

    private val _isBluetoothAudioConnected = MutableStateFlow(false)
    val isBluetoothAudioConnected: StateFlow<Boolean> = _isBluetoothAudioConnected.asStateFlow()

    private val _connectedAudioDeviceName = MutableStateFlow<String?>(null)
    val connectedAudioDeviceName: StateFlow<String?> = _connectedAudioDeviceName.asStateFlow()

    private var isInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        if (!isInitialized) {
            val prefs = getPrefs(context)
            _preferredSlot.value = prefs.getInt(KEY_PREFERRED_SIM_SLOT, SLOT_SYSTEM_DEFAULT)
            _audioRoute.value = prefs.getString(KEY_AUDIO_ROUTE, AUDIO_SPEAKERPHONE) ?: AUDIO_SPEAKERPHONE
            isInitialized = true
        }
        refreshSims(context)
        checkBluetoothAudioDevices(context)
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
        Log.d(TAG, "Audio route changed to: $route")
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
     * Inspects connected Bluetooth audio devices (HFP/SCO or Headsets)
     */
    fun checkBluetoothAudioDevices(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        var hasBt = false
        var devName: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val commDevices = audioManager.availableCommunicationDevices
            val bt = commDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
            if (bt != null) {
                hasBt = true
                devName = bt.productName?.toString() ?: "Périphérique Bluetooth"
            }
        } else {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val bt = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
            if (bt != null) {
                hasBt = true
                devName = bt.productName?.toString() ?: "Périphérique Bluetooth"
            }
        }

        _isBluetoothAudioConnected.value = hasBt
        _connectedAudioDeviceName.value = devName
        return hasBt
    }

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
     * Applies the configured audio route with multi-stage pulses to ensure
     * that even if the system dialer UI resets audio state during call establishment,
     * the requested routing (Speakerphone, Bluetooth SCO, or Earpiece) stays locked.
     */
    fun applyCallAudioRoute(context: Context) {
        val route = _audioRoute.value
        Log.d(TAG, "Requesting audio route: $route")

        // 1. Immediate Telecom InCallService routing if available
        BridgeInCallService.applyAudioRoute(route)

        // 2. Hardware AudioManager enforcement schedule (0ms, 400ms, 1200ms, 2500ms, 4000ms)
        val delays = listOf(50L, 400L, 1200L, 2500L, 4000L)
        for (delay in delays) {
            mainHandler.postDelayed({
                executeDirectAudioRoute(context, route)
                BridgeInCallService.applyAudioRoute(route)
            }, delay)
        }
    }

    /**
     * Executes the direct audio routing commands against AudioManager
     */
    private fun executeDirectAudioRoute(context: Context, route: String) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            // Unmute voice call audio and ensure volume is audible
            try {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                if (curVol < maxVol / 2) {
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVol * 0.85).toInt(), 0)
                }
            } catch (e: Exception) {
                // non fatal
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Modern Android (API 31+ Android 12, 13, 14, 15)
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                val commDevices = audioManager.availableCommunicationDevices

                when (route) {
                    AUDIO_SPEAKERPHONE -> {
                        val speaker = commDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        if (speaker != null) {
                            val ok = audioManager.setCommunicationDevice(speaker)
                            Log.d(TAG, "setCommunicationDevice(SPEAKER): success=$ok")
                        }
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                    }
                    AUDIO_BLUETOOTH -> {
                        val btDevice = commDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        }
                        if (btDevice != null) {
                            val ok = audioManager.setCommunicationDevice(btDevice)
                            Log.d(TAG, "setCommunicationDevice(BLUETOOTH): success=$ok (${btDevice.productName})")
                        } else {
                            Log.w(TAG, "No Bluetooth device in availableCommunicationDevices, trying startBluetoothSco")
                            @Suppress("DEPRECATION")
                            audioManager.startBluetoothSco()
                            @Suppress("DEPRECATION")
                            audioManager.isBluetoothScoOn = true
                        }
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = false
                    }
                    AUDIO_EARPIECE -> {
                        audioManager.clearCommunicationDevice()
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = false
                        @Suppress("DEPRECATION")
                        if (audioManager.isBluetoothScoOn) {
                            audioManager.stopBluetoothSco()
                            audioManager.isBluetoothScoOn = false
                        }
                    }
                }
            } else {
                // Legacy Android (API 26-30)
                try {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                } catch (e: Exception) {
                    // ignore
                }

                when (route) {
                    AUDIO_SPEAKERPHONE -> {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                        Log.d(TAG, "Legacy Speakerphone ON")
                    }
                    AUDIO_BLUETOOTH -> {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = false
                        @Suppress("DEPRECATION")
                        audioManager.startBluetoothSco()
                        @Suppress("DEPRECATION")
                        audioManager.isBluetoothScoOn = true
                        Log.d(TAG, "Legacy Bluetooth SCO ON")
                    }
                    AUDIO_EARPIECE -> {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = false
                        @Suppress("DEPRECATION")
                        if (audioManager.isBluetoothScoOn) {
                            audioManager.stopBluetoothSco()
                            audioManager.isBluetoothScoOn = false
                        }
                        Log.d(TAG, "Legacy Earpiece ON")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeDirectAudioRoute failed for $route", e)
        }
    }

    /**
     * Toggles Speakerphone state directly during a call and informs BlackBerry
     */
    fun toggleSpeakerphone(context: Context): Boolean {
        val current = _audioRoute.value
        val newRoute = if (current == AUDIO_SPEAKERPHONE) AUDIO_EARPIECE else AUDIO_SPEAKERPHONE
        setAudioRoute(context, newRoute)
        applyCallAudioRoute(context)

        val isSpeakerOn = (newRoute == AUDIO_SPEAKERPHONE)
        val status = if (isSpeakerOn) "ON" else "OFF"
        BluetoothService.instance?.sendPacket(BSBPacket("SPEAKER_STATUS", listOf(status)))
        BridgeStateManager.logEvent("Haut-parleur commuté: $status", EventType.INFO)
        return isSpeakerOn
    }
}
