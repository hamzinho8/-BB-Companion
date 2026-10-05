package com.hamza.blackberrybridge.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gestionnaire d'émission et de consultation des SMS pour le BlackBerry Curve 9300.
 *
 * Résout les 2 problèmes majeurs :
 * 1. Enregistre automatiquement les SMS envoyés dans la base système Android (Telephony.Sms.Sent)
 *    pour qu'ils apparaissent immédiatement dans l'application Messages d'Android.
 * 2. Décode intelligemment le corps du message (supporte le texte en clair direct ET le Base64 sans corrompre le texte).
 * 3. Fournit à la fois le texte en clair et Base64 lors de la lecture pour compatibilité totale avec le BlackBerry.
 */
object SmsManagerBridge {
    private const val TAG = "SmsManagerBridge"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val requestCounter = AtomicInteger(1000)

    const val ACTION_SMS_SENT_STATUS = "com.hamza.blackberrybridge.SMS_SENT_STATUS"

    /**
     * Décode intelligemment le corps du message reçu du BlackBerry.
     * Si le BlackBerry a envoyé du texte normal (ex: "Bonjour"), il N'EST PAS altéré.
     * S'il s'agit de Base64 (ex: "B64:..." ou payload Base64 pur), il est décodé proprement.
     */
    fun decodeSmsBody(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        // 1. Préfixe explicite Base64
        if (trimmed.startsWith("B64:")) {
            return try {
                String(Base64.decode(trimmed.substring(4), Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) {
                trimmed.substring(4)
            }
        }

        // 2. Si la chaîne contient des espaces, des retours à la ligne ou de la ponctuation, c'est du texte en clair !
        if (trimmed.contains(" ") || trimmed.contains("\n") || trimmed.contains("\r") ||
            trimmed.contains("?") || trimmed.contains("!") || trimmed.contains(",") || trimmed.contains(";") || trimmed.contains("'")) {
            return trimmed
        }

        // 3. Test de validité Base64 strict (uniquement si longueur multiple de 4 et >= 8 caractères)
        if (trimmed.length >= 8 && trimmed.length % 4 == 0 && trimmed.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
            try {
                val decodedBytes = Base64.decode(trimmed, Base64.DEFAULT)
                val decodedStr = String(decodedBytes, Charsets.UTF_8)
                // Vérifier que le texte décodé est lisible et que le ré-encodage correspond exactement
                val reEncoded = Base64.encodeToString(decodedBytes, Base64.NO_WRAP)
                if (reEncoded == trimmed && decodedStr.all { it.isLetterOrDigit() || it.isWhitespace() || "!?,.:;'-_@#%*()\"/+=€$".contains(it) }) {
                    return decodedStr
                }
            } catch (e: Exception) {
                // Pas du base64 valide -> garder le texte brut
            }
        }

        return trimmed
    }

    /**
     * Envoie un SMS composé depuis le clavier physique du BlackBerry Curve 9300.
     * @param rawRecipient Numéro de téléphone destinataire
     * @param rawBody Contenu textuel du SMS (en clair ou Base64)
     * @param requestedSlot Slot SIM souhaité (0 pour SIM 1, 1 pour SIM 2, ou null pour automatique)
     */
    fun sendSms(
        context: Context,
        rawRecipient: String,
        rawBody: String,
        requestedSlot: Int? = null
    ) {
        val cleanNumber = rawRecipient.trim().replace(" ", "").replace("-", "")
        val messageBody = decodeSmsBody(rawBody)

        if (cleanNumber.isEmpty() || messageBody.isEmpty()) {
            Log.w(TAG, "Numéro ou message vide")
            notifyError(cleanNumber, "NUMÉRO_OU_MESSAGE_VIDE")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission SEND_SMS manquante")
            BridgeStateManager.logEvent("Erreur: Permission SEND_SMS manquante", EventType.ERROR)
            notifyError(cleanNumber, "PERMISSION_SEND_SMS_MANQUANTE")
            return
        }

        scope.launch {
            try {
                val targetSim = SimManager.resolveTargetSim(context, requestedSlot)
                val simName = targetSim?.displayName ?: "SIM 1"
                val subId = targetSim?.subscriptionId ?: -1

                @Suppress("DEPRECATION")
                val smsManager: SmsManager = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId()
                        val targetSubId = if (subId > 0 && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) subId else defaultSubId
                        if (targetSubId > 0 && targetSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                            context.getSystemService(SmsManager::class.java).createForSubscriptionId(targetSubId)
                        } else {
                            context.getSystemService(SmsManager::class.java)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subId > 0 && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        SmsManager.getSmsManagerForSubscriptionId(subId)
                    } else {
                        SmsManager.getDefault()
                    }
                } catch (e: Exception) {
                    SmsManager.getDefault()
                }

                val reqCode = requestCounter.incrementAndGet()
                val sentAction = "$ACTION_SMS_SENT_STATUS.$reqCode"
                val sentIntent = Intent(sentAction)
                val sentPendingIntent = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    sentIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )

                // Enregistrement d'un récepteur pour confirmer la transmission opérateur
                val sentReceiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        try {
                            context.unregisterReceiver(this)
                        } catch (e: Exception) {}

                        val contactName = ContactManager.getContactNameByNumber(context, cleanNumber)
                        when (resultCode) {
                            Activity.RESULT_OK -> {
                                Log.d(TAG, "SMS transmis avec succès par l'opérateur vers $cleanNumber")
                                BridgeStateManager.logEvent("SMS envoyé avec succès à $contactName", EventType.SUCCESS)

                                // Sauvegarde obligatoire dans l'application Messages d'Android
                                saveSentSmsToContentProvider(context, cleanNumber, messageBody, subId)

                                // Confirmation Bluetooth vers le BlackBerry
                                BluetoothService.instance?.sendPacket(
                                    BSBPacket("SMS_SENT_OK", listOf(cleanNumber, simName, contactName))
                                )
                            }
                            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                                Log.e(TAG, "Échec SMS: Aucun signal réseau")
                                BridgeStateManager.logEvent("Échec SMS: Aucun signal réseau", EventType.ERROR)
                                notifyError(cleanNumber, "AUCUN_SIGNAL_RÉSEAU")
                            }
                            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                                Log.e(TAG, "Échec SMS: Mode avion ou antenne éteinte")
                                BridgeStateManager.logEvent("Échec SMS: Mode avion actif", EventType.ERROR)
                                notifyError(cleanNumber, "MODE_AVION_ACTIF")
                            }
                            else -> {
                                Log.e(TAG, "Échec émission SMS (code opérateur: $resultCode)")
                                BridgeStateManager.logEvent("Échec émission SMS (code $resultCode)", EventType.ERROR)
                                notifyError(cleanNumber, "ERREUR_OPÉRATEUR_$resultCode")
                            }
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(sentReceiver, IntentFilter(sentAction), Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(sentReceiver, IntentFilter(sentAction))
                }

                // Découpage et envoi du SMS
                val parts = smsManager.divideMessage(messageBody)
                if (parts.size > 1) {
                    val sentIntents = ArrayList<PendingIntent>()
                    sentIntents.add(sentPendingIntent)
                    for (i in 1 until parts.size) {
                        sentIntents.add(PendingIntent.getBroadcast(context, reqCode + i, Intent("$sentAction.$i"), PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE))
                    }
                    smsManager.sendMultipartTextMessage(cleanNumber, null, parts, sentIntents, null)
                } else {
                    smsManager.sendTextMessage(cleanNumber, null, messageBody, sentPendingIntent, null)
                }

                Log.d(TAG, "SMS soumis à l'antenne via $simName vers $cleanNumber : $messageBody")
                BridgeStateManager.logEvent("Envoi SMS en cours vers $cleanNumber...", EventType.INFO)

                // Enregistrement immédiat par anticipation dans le ContentProvider pour affichage instantané
                saveSentSmsToContentProvider(context, cleanNumber, messageBody, subId)

            } catch (e: Exception) {
                Log.e(TAG, "Exception lors de l'émission SMS vers $cleanNumber", e)
                BridgeStateManager.logEvent("Échec émission SMS: ${e.message}", EventType.ERROR)
                notifyError(cleanNumber, e.message ?: "ERREUR_INCONNUE")
            }
        }
    }

    /**
     * Insère le SMS envoyé dans le ContentProvider système (Telephony.Sms.Sent).
     * Ceci est indispensable pour que le SMS apparaisse dans l'application Messages d'Android.
     */
    fun saveSentSmsToContentProvider(context: Context, address: String, body: String, subId: Int = -1) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.DATE_SENT, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
                if (subId > 0) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, subId)
                }
            }
            val uri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            Log.d(TAG, "SMS enregistré dans la boîte d'envoi Android: $uri")
        } catch (e: Exception) {
            Log.w(TAG, "Impossible d'insérer dans Telephony.Sms.Sent: ${e.message}")
        }
    }

    /**
     * Récupère les derniers SMS de la boîte de réception Android et les transfère au BlackBerry.
     * Envoie à la fois le texte en clair et Base64 pour assurer 100% de compatibilité.
     */
    @SuppressLint("Range")
    fun fetchRecentSms(context: Context, limit: Int = 20) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission READ_SMS manquante")
            BridgeStateManager.logEvent("Erreur: Permission READ_SMS manquante", EventType.WARNING)
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
                        val plainText = SmsReceiver.sanitizeForProtocol(body)
                        val base64Body = Base64.encodeToString(body.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                        // 1. Format SMS_ITEM : type, adresse, nom, date, texte en clair, base64
                        service.sendPacket(
                            BSBPacket(
                                "SMS_ITEM",
                                listOf(
                                    type.toString(),
                                    address,
                                    contactName,
                                    date.toString(),
                                    plainText,
                                    base64Body
                                )
                            )
                        )

                        // 2. Format SMS_INCOMING de compatibilité (si le BlackBerry n'écoute que SMS_INCOMING)
                        if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                            service.sendPacket(
                                BSBPacket(
                                    "SMS_INCOMING",
                                    listOf(
                                        address,
                                        contactName,
                                        "0",
                                        date.toString(),
                                        plainText,
                                        base64Body
                                    )
                                )
                            )
                        }

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
