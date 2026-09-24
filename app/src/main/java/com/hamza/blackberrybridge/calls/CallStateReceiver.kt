package com.hamza.blackberrybridge.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.hamza.blackberrybridge.settings.SettingsDataStore
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket
import com.hamza.blackberrybridge.contacts.ContactManager
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.state.EventType
import com.hamza.blackberrybridge.telephony.SimManager

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            context?.let { ctx ->
                val dataStore = SettingsDataStore(ctx)
                val allowCalls = runBlocking { dataStore.allowCallsFlow.first() }
                if (!allowCalls) return
                
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Inconnu"
                
                val callId = "call_${System.currentTimeMillis()}"

                // Detect on which SIM the call is arriving
                val subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
                val slot = intent.getIntExtra("simSlot", intent.getIntExtra("slot", -1))
                val sims = SimManager.refreshSims(ctx)
                val simInfo = sims.firstOrNull { it.subscriptionId == subId || (slot >= 0 && it.slotIndex == slot) }
                val simName = simInfo?.displayName ?: if (sims.isNotEmpty()) sims.first().displayName else "SIM"
                
                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        val callerName = ContactManager.getContactNameByNumber(ctx, incomingNumber)
                        BridgeStateManager.logEvent("Appel entrant [$simName]: $callerName ($incomingNumber)", EventType.INFO)
                        BluetoothService.instance?.sendPacket(
                            BSBPacket("CALL_INCOMING", listOf(callId, callerName, incomingNumber, simName))
                        )
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        BridgeStateManager.logEvent("Appel en cours [$simName]", EventType.INFO)
                        // S'assurer que le routage audio (haut-parleur / Bluetooth) est bien enclenché dès le décrochage
                        SimManager.applyCallAudioRoute(ctx)
                        BluetoothService.instance?.sendPacket(BSBPacket("CALL_ACTIVE", listOf(callId, simName)))
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        BridgeStateManager.logEvent("Appel terminé", EventType.INFO)
                        BluetoothService.instance?.sendPacket(BSBPacket("CALL_END", listOf(callId)))
                    }
                }
            }
        }
    }
}
