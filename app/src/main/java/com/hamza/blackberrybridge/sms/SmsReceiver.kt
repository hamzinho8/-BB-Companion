package com.hamza.blackberrybridge.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Base64
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.contacts.ContactManager
import com.hamza.blackberrybridge.protocol.BSBPacket
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.state.EventType

/**
 * Récepteur Broadcast pour les SMS entrants sur Android.
 * Transfère instantanément le SMS au BlackBerry Curve 9300 via Bluetooth.
 */
class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages: Array<SmsMessage> = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            if (messages.isEmpty()) return

            val senderNumber = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: "Inconnu"
            val contactName = ContactManager.getContactNameByNumber(context, senderNumber)
            val timestamp = messages[0].timestampMillis

            // Reconstitution du texte complet (SMS concaténés / multi-parties)
            val fullBodyBuilder = StringBuilder()
            for (msg in messages) {
                fullBodyBuilder.append(msg.displayMessageBody ?: msg.messageBody ?: "")
            }
            val fullBody = fullBodyBuilder.toString()

            // Détection du Slot SIM récepteur (support constructeurs Qualcomm / MTK / Android standard)
            var simSlot = intent.getIntExtra("slot", -1)
            if (simSlot == -1) simSlot = intent.getIntExtra("simId", -1)
            if (simSlot == -1) simSlot = intent.getIntExtra("sim_slot", -1)
            if (simSlot == -1) {
                val subId = intent.getIntExtra("subscription", -1)
                if (subId != -1) {
                    val sim = com.hamza.blackberrybridge.telephony.SimManager.availableSims.value.firstOrNull { it.subscriptionId == subId }
                    if (sim != null) simSlot = sim.slotIndex
                }
            }
            if (simSlot == -1) simSlot = 0

            Log.d(TAG, "SMS reçu de $senderNumber ($contactName) sur SIM $simSlot : ${fullBody.take(40)}...")
            BridgeStateManager.logEvent("SMS reçu de $contactName [SIM ${simSlot + 1}]", EventType.SUCCESS)

            // Encodage Base64 strict NO_WRAP pour préserver accents, retours à la ligne et caractères spéciaux
            val base64Body = Base64.encodeToString(fullBody.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            // Transmission Bluetooth vers le BlackBerry Curve 9300
            val service = BluetoothService.instance
            if (service != null && BridgeStateManager.isConnected.value) {
                service.sendPacket(
                    BSBPacket(
                        "SMS_INCOMING",
                        listOf(
                            senderNumber,
                            contactName,
                            simSlot.toString(),
                            timestamp.toString(),
                            base64Body
                        )
                    )
                )
                Log.d(TAG, "SMS transmis au BlackBerry Curve 9300 !")
            } else {
                Log.d(TAG, "BlackBerry non connecté - SMS enregistré en local")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erreur traitement SMS entrant", e)
            BridgeStateManager.logEvent("Erreur réception SMS: ${e.message}", EventType.ERROR)
        }
    }
}
