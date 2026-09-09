with open('app/src/main/java/com/hamza/blackberrybridge/contacts/ContactManager.kt', 'r') as f:
    content = f.read()

helper_method = """
    @SuppressLint("Range")
    fun getContactNameByNumber(context: Context, phoneNumber: String): String {
        if (phoneNumber.isEmpty() || phoneNumber == "Inconnu" || phoneNumber == "Unknown") return phoneNumber
        try {
            val uri = android.net.Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(phoneNumber))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)) ?: phoneNumber
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name", e)
        }
        return phoneNumber
    }
"""

content = content.replace('object ContactManager {\n    private const val TAG', 'object ContactManager {\n    private const val TAG' + helper_method)

with open('app/src/main/java/com/hamza/blackberrybridge/contacts/ContactManager.kt', 'w') as f:
    f.write(content)

# Update CallStateReceiver
with open('app/src/main/java/com/hamza/blackberrybridge/calls/CallStateReceiver.kt', 'r') as f:
    receiver_content = f.read()

new_receiver_content = """package com.hamza.blackberrybridge.calls

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
                
                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        val callerName = ContactManager.getContactNameByNumber(ctx, incomingNumber)
                        BridgeStateManager.logEvent("Appel entrant: $callerName ($incomingNumber)", EventType.INFO)
                        BluetoothService.instance?.sendPacket(BSBPacket("CALL_INCOMING", listOf(callId, callerName, incomingNumber)))
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        BridgeStateManager.logEvent("Appel décroché", EventType.INFO)
                        BluetoothService.instance?.sendPacket(BSBPacket("CALL_ACTIVE", listOf(callId)))
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
"""

with open('app/src/main/java/com/hamza/blackberrybridge/calls/CallStateReceiver.kt', 'w') as f:
    f.write(new_receiver_content)

