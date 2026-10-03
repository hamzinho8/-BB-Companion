package com.hamza.blackberrybridge.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.contacts.ContactManager
import com.hamza.blackberrybridge.protocol.BSBPacket
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.state.EventType
import com.hamza.blackberrybridge.telephony.SimManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Gestionnaire d'émission et de consultation des SMS pour le BlackBerry Curve 9300.
 * Permet d'envoyer des SMS rédigés sur le clavier physique du BlackBerry via Android
 * avec sélection de la SIM et accusé d'envoi.
 */
object SmsManagerBridge {
    private const val TAG = "SmsManagerBridge"
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Envoie un SMS composé depuis le clavier physique du BlackBerry Curve 9300.
     * @param rawRecipient Numéro de téléphone destinataire
     * @param messageBody Contenu textuel du SMS
     * @param requestedSlot Slot SIM souhaité (0 pour SIM 1, 1 pour SIM 2, ou null pour automatique)
     */
    fun sendSms(
        context: Context,
        rawRecipient: String,
        messageBody: String,
        requestedSlot: Int? = null
    ) {
        val cleanNumber = rawRecipient.trim().replace(" ", "").replace("-", "")
        if (cleanNumber.isEmpty() || messageBody.isEmpty()) {
            notifyError(cleanNumber, "NUMÉRO_OU_MESSAGE_VIDE")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission SEND_SMS manquante")
            BridgeStateManager.logEvent("Erreur: Permission SEND_SMS non accordée", EventType.ERROR)
            notifyError(cleanNumber, "PERMISSION_SEND_SMS_MANQUANTE")
            return
        }

        scope.launch {
            try {
                val targetSim = SimManager.resolveTargetSim(context, requestedSlot)
                val simName = targetSim?.displayName ?: "SIM 1"
                val subId = targetSim?.subscriptionId ?: -1

                @Suppress("DEPRECATION")
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && subId > 0) {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subId > 0) {
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                } else {
                    SmsManager.getDefault()
                }

                val parts = smsManager.divideMessage(messageBody)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(cleanNumber, null, messageBody, null, null)
                }

                val contactName = ContactManager.getContactNameByNumber(context, cleanNumber)
                Log.d(TAG, "SMS transmis via $simName vers $cleanNumber ($contactName)")
                BridgeStateManager.logEvent("SMS envoyé à $contactName via $simName", EventType.SUCCESS)

                // Confirmation au BlackBerry : "SMS_SENT_OK|<destinataire>|<simName>\n"
                BluetoothService.instance?.sendPacket(
                    BSBPacket("SMS_SENT_OK", listOf(cleanNumber, simName, contactName))
                )

            } catch (e: Exception) {
                Log.e(TAG, "Échec émission SMS vers $cleanNumber", e)
                BridgeStateManager.logEvent("Échec SMS vers $cleanNumber: ${e.message}", EventType.ERROR)
                notifyError(cleanNumber, e.message ?: "ERREUR_INCONNUE")
            }
        }
    }

    /**
     * Récupère les derniers SMS de la boîte de réception Android et les transfère au BlackBerry.
     */
    @SuppressLint("Range")
    fun fetchRecentSms(context: Context, limit: Int = 20) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission READ_SMS manquante")
            BluetoothService.instance?.sendPacket(BSBPacket("SMS_LIST_ERROR", listOf("PERMISSION_MANQUANTE")))
            return
        }

        scope.launch {
            val service = BluetoothService.instance ?: return@launch
            var count = 0

            try {
                val projection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                )

                val uri = Telephony.Sms.CONTENT_URI
                val cursor = context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC LIMIT $limit"
                )

                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val address = c.getString(c.getColumnIndex(Telephony.Sms.ADDRESS)) ?: ""
                        val body = c.getString(c.getColumnIndex(Telephony.Sms.BODY)) ?: ""
                        val date = c.getLong(c.getColumnIndex(Telephony.Sms.DATE))
                        val type = c.getInt(c.getColumnIndex(Telephony.Sms.TYPE)) // 1: Reçu, 2: Envoyé

                        val contactName = ContactManager.getContactNameByNumber(context, address)
                        val base64Body = Base64.encodeToString(body.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                        // Format du paquet : SMS_ITEM|<type>|<address>|<contactName>|<date>|<base64Body>
                        service.sendPacket(
                            BSBPacket(
                                "SMS_ITEM",
                                listOf(
                                    type.toString(),
                                    address,
                                    contactName,
                                    date.toString(),
                                    base64Body
                                )
                            )
                        )
                        count++
                    }
                }

                // Clôture du transfert : SMS_LIST_END|<count>
                service.sendPacket(BSBPacket("SMS_LIST_END", listOf(count.toString())))
                Log.d(TAG, "Synchronisation SMS terminée ($count messages envoyés)")
                BridgeStateManager.logEvent("Synchro SMS terminée ($count messages)", EventType.INFO)

            } catch (e: Exception) {
                Log.e(TAG, "Erreur lecture SMS", e)
                service.sendPacket(BSBPacket("SMS_LIST_ERROR", listOf(e.message ?: "ERREUR_LECTURE")))
            }
        }
    }

    private fun notifyError(recipient: String, reason: String) {
        BluetoothService.instance?.sendPacket(
            BSBPacket("SMS_SENT_ERROR", listOf(recipient, reason))
        )
    }
}
