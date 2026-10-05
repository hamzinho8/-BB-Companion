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
 * Fournit à la fois le texte en clair sécurisé ET en Base64 pour compatibilité 100% avec l'app BlackBerry.
 */
class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"

        fun sanitizeForProtocol(text: String): String {
            return text.replace("|", "-").replace("\n", " ").replace("\r", " ").trim()
        }
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

            // Détection du Slot SIM récepteur
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
            BridgeStateManager.logEvent("SMS reçu de $contactName : ${fullBody.take(30)}", EventType.SUCCESS)

            val plainText = sanitizeForProtocol(fullBody)
            val base64Body = Base64.encodeToString(fullBody.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            // Transmission Bluetooth vers le BlackBerry Curve 9300
            val service = BluetoothService.instance
            if (service != null && BridgeStateManager.isConnected.value) {
                // Format universel : SMS_INCOMING|<numéro>|<nomContact>|<slotSIM>|<timestamp>|<texteEnClair>|<base64>
                service.sendPacket(
                    BSBPacket(
                        "SMS_INCOMING",
                        listOf(
                            senderNumber,
                            contactName,
                            simSlot.toString(),
                            timestamp.toString(),
                            plainText,
                            base64Body
                        )
                    )
                )

                // Envoi d'un alias SMS_MSG pour les implémentations BlackBerry alternatives
                service.sendPacket(
                    BSBPacket(
                        "SMS_MSG",
                        listOf(
                            senderNumber,
                            contactName,
                            plainText,
                            timestamp.toString()
                        )
                    )
                )

                Log.d(TAG, "SMS transmis au BlackBerry Curve 9300 (clair + base64) !")
            } else {
                Log.d(TAG, "BlackBerry non connecté - SMS enregistré en local")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erreur traitement SMS entrant", e)
            BridgeStateManager.logEvent("Erreur réception SMS: ${e.message}", EventType.ERROR)
        }
    }
}
