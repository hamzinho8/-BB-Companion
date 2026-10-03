package com.hamza.blackberrybridge.whatsapp

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import com.hamza.blackberrybridge.audio.AudioStreamManager
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.state.EventType
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestionnaire Complet WhatsApp pour BlackBerry Curve 9300.
 *
 * Fonctionnalités prises en charge :
 * 1. Appels WhatsApp Entrants :
 *    - Détection de l'appel entrant (Nom de l'appelant)
 *    - Décrochage depuis la touche verte du BlackBerry (Touche Appel)
 *    - Rejet ou fin d'appel depuis la touche rouge du BlackBerry (Touche Fin)
 *    - Routage automatique du son 16 kHz HD vers le BlackBerry
 * 2. Messages WhatsApp :
 *    - Réception des messages (texte intégral avec décodage Base64, nom de contact, groupe)
 *    - Réponse rapide rédigée au clavier physique AZERTY/QWERTY du Curve 9300
 *    - Envoi instantané via RemoteInput sans déverrouiller le smartphone
 * 3. Lancement de discussion WhatsApp depuis le BlackBerry.
 */
object WhatsAppBridgeManager {
    private const val TAG = "WhatsAppBridgeManager"

    const val PACKAGE_WHATSAPP = "com.whatsapp"
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    // Registre des actions d'appels actifs
    private val callAnswerActions = ConcurrentHashMap<String, PendingIntent>()
    private val callRejectActions = ConcurrentHashMap<String, PendingIntent>()
    private val callCallerNames = ConcurrentHashMap<String, String>()
    var activeWhatsAppCallId: String? = null
        private set

    // Registre des actions de réponse aux messages
    private val messageReplyActions = ConcurrentHashMap<String, Notification.Action>()
    private val lastProcessedMsgTime = ConcurrentHashMap<String, Long>()

    fun isWhatsAppPackage(packageName: String): Boolean {
        return packageName == PACKAGE_WHATSAPP || packageName == PACKAGE_WHATSAPP_BUSINESS
    }

    /**
     * Analyse une notification WhatsApp (Appel ou Message) et la traite en temps réel.
     * @return true si la notification a été traitée par ce module.
     */
    fun handleNotificationPosted(context: Context, sbn: StatusBarNotification): Boolean {
        val packageName = sbn.packageName
        if (!isWhatsAppPackage(packageName)) return false

        val notif = sbn.notification ?: return false
        val extras = notif.extras ?: return false
        val notifId = sbn.key

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val category = notif.category

        // 1. Détection des APPELS WhatsApp Entrants
        val isCallNotification = category == Notification.CATEGORY_CALL ||
                title.contains("appel", ignoreCase = true) ||
                text.contains("appel", ignoreCase = true) ||
                text.contains("sonne", ignoreCase = true) ||
                text.contains("incoming", ignoreCase = true)

        if (isCallNotification) {
            var answerIntent: PendingIntent? = null
            var rejectIntent: PendingIntent? = null

            notif.actions?.forEach { action ->
                val actionTitle = action.title?.toString()?.lowercase() ?: ""
                if (actionTitle.contains("répondre") || actionTitle.contains("answer") || actionTitle.contains("décrocher") || actionTitle.contains("accepter")) {
                    answerIntent = action.actionIntent
                } else if (actionTitle.contains("refuser") || actionTitle.contains("decline") || actionTitle.contains("rejeter") || actionTitle.contains("ignorer") || actionTitle.contains("raccrocher")) {
                    rejectIntent = action.actionIntent
                }
            }

            if (answerIntent != null || rejectIntent != null) {
                activeWhatsAppCallId = notifId
                if (answerIntent != null) callAnswerActions[notifId] = answerIntent!!
                if (rejectIntent != null) callRejectActions[notifId] = rejectIntent!!
                val callerName = title.ifEmpty { "Appel WhatsApp" }
                callCallerNames[notifId] = callerName

                Log.d(TAG, "Appel WhatsApp entrant détecté de: $callerName (ID: $notifId)")
                BridgeStateManager.logEvent("Appel WhatsApp entrant de $callerName", EventType.WARNING)

                // Envoi vers le BlackBerry : "WHATSAPP_CALL_INCOMING|<callId>|<callerName>\n"
                val service = BluetoothService.instance
                service?.sendPacket(BSBPacket("WHATSAPP_CALL_INCOMING", listOf(notifId, callerName)))
                return true
            }
        }

        // 2. Détection des MESSAGES WhatsApp Entrants
        var replyAction: Notification.Action? = null
        notif.actions?.forEach { action ->
            if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                replyAction = action
            }
        }

        if (replyAction != null) {
            messageReplyActions[notifId] = replyAction!!

            // Anti-doublon (skip les mises à jour rapprochées < 2000ms du même message)
            val dedupeKey = "$title:$text"
            val now = System.currentTimeMillis()
            val prevTime = lastProcessedMsgTime[dedupeKey] ?: 0L
            if (now - prevTime < 2000L) {
                return true
            }
            lastProcessedMsgTime[dedupeKey] = now

            val senderName = title.ifEmpty { "WhatsApp" }
            val messageBody = text.ifEmpty { "Nouveau message" }

            Log.d(TAG, "Message WhatsApp reçu de $senderName: ${messageBody.take(40)}...")
            BridgeStateManager.logEvent("WhatsApp de $senderName: ${messageBody.take(30)}", EventType.INFO)

            // Encodage Base64 strict NO_WRAP pour préserver les caractères spéciaux
            val base64Body = Base64.encodeToString(messageBody.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val timestamp = sbn.postTime

            // Format du paquet : "WHATSAPP_MSG|<notifId>|<senderName>|<base64Body>|<timestamp>\n"
            val service = BluetoothService.instance
            service?.sendPacket(
                BSBPacket(
                    "WHATSAPP_MSG",
                    listOf(notifId, senderName, base64Body, timestamp.toString())
                )
            )
            return true
        }

        return false
    }

    /**
     * Traite la disparition d'une notification WhatsApp (Fin d'appel, message lu).
     */
    fun handleNotificationRemoved(sbn: StatusBarNotification) {
        val notifId = sbn.key
        if (notifId == activeWhatsAppCallId) {
            Log.d(TAG, "Appel WhatsApp terminé (notification supprimée)")
            BridgeStateManager.logEvent("Appel WhatsApp terminé", EventType.INFO)
            activeWhatsAppCallId = null
            callAnswerActions.remove(notifId)
            callRejectActions.remove(notifId)
            callCallerNames.remove(notifId)

            val service = BluetoothService.instance
            service?.sendPacket(BSBPacket("WHATSAPP_CALL_ENDED", listOf(notifId)))
        }
        messageReplyActions.remove(notifId)
    }

    /**
     * Décroche un appel WhatsApp suite à l'appui sur la Touche Appel (Verte) du BlackBerry.
     */
    fun answerCall(context: Context, callId: String = "") {
        val targetId = if (callId.isNotEmpty()) callId else activeWhatsAppCallId
        val pendingIntent = if (targetId != null) callAnswerActions[targetId] else null

        if (pendingIntent != null) {
            try {
                pendingIntent.send()
                Log.d(TAG, "Appel WhatsApp décroché avec succès via PendingIntent")
                BridgeStateManager.logEvent("Appel WhatsApp décroché depuis BlackBerry", EventType.SUCCESS)

                val service = BluetoothService.instance
                service?.sendPacket(BSBPacket("WHATSAPP_CALL_ANSWER_OK", listOf(targetId ?: "")))

                // Démarrage automatique de la passerelle audio 16 kHz HD
                service?.let { AudioStreamManager.start(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur décrochage appel WhatsApp", e)
                BridgeStateManager.logEvent("Échec décrochage WhatsApp: ${e.message}", EventType.ERROR)
            }
        } else {
            Log.w(TAG, "Aucune action de décrochage trouvée pour l'appel WhatsApp $callId")
        }
    }

    /**
     * Rejette ou raccroche un appel WhatsApp suite à l'appui sur la Touche Fin (Rouge) du BlackBerry.
     */
    fun rejectCall(context: Context, callId: String = "") {
        val targetId = if (callId.isNotEmpty()) callId else activeWhatsAppCallId
        val pendingIntent = if (targetId != null) callRejectActions[targetId] else null

        if (pendingIntent != null) {
            try {
                pendingIntent.send()
                Log.d(TAG, "Appel WhatsApp rejeté/raccroché avec succès")
                BridgeStateManager.logEvent("Appel WhatsApp rejeté depuis BlackBerry", EventType.WARNING)

                val service = BluetoothService.instance
                service?.sendPacket(BSBPacket("WHATSAPP_CALL_REJECT_OK", listOf(targetId ?: "")))

                // Arrêt de la diffusion audio
                AudioStreamManager.stop(stopProjectionService = true)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur rejet appel WhatsApp", e)
            } finally {
                activeWhatsAppCallId = null
            }
        } else {
            Log.w(TAG, "Aucune action de rejet trouvée pour l'appel WhatsApp $callId")
            // Par sécurité, couper le son
            AudioStreamManager.stop(stopProjectionService = true)
            activeWhatsAppCallId = null
        }
    }

    /**
     * Envoie une réponse textuelle WhatsApp rédigée au clavier physique du BlackBerry.
     */
    fun replyMessage(context: Context, notifId: String, replyText: String) {
        val action = messageReplyActions[notifId]
        val service = BluetoothService.instance

        if (action != null && action.remoteInputs != null) {
            val intent = Intent()
            val bundle = Bundle()
            for (input in action.remoteInputs) {
                bundle.putCharSequence(input.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)

            try {
                action.actionIntent.send(context, 0, intent)
                Log.d(TAG, "Réponse WhatsApp envoyée pour $notifId : $replyText")
                BridgeStateManager.logEvent("WhatsApp répondu: $replyText", EventType.SUCCESS)

                // Confirmation au BlackBerry : "WHATSAPP_REPLY_OK|<notifId>\n"
                service?.sendPacket(BSBPacket("WHATSAPP_REPLY_OK", listOf(notifId)))
            } catch (e: Exception) {
                Log.e(TAG, "Échec envoi réponse WhatsApp", e)
                BridgeStateManager.logEvent("Échec réponse WhatsApp: ${e.message}", EventType.ERROR)
                service?.sendPacket(BSBPacket("WHATSAPP_REPLY_ERROR", listOf(notifId, e.message ?: "ERREUR")))
            }
        } else {
            Log.w(TAG, "Impossible de répondre : action introuvable ou expirée pour $notifId")
            service?.sendPacket(BSBPacket("WHATSAPP_REPLY_ERROR", listOf(notifId, "ACTION_EXPIREE")))
        }
    }

    /**
     * Ouvre une conversation WhatsApp directement vers un numéro donné.
     */
    fun startChat(context: Context, rawNumber: String, initialMessage: String = "") {
        val cleanNumber = rawNumber.trim().replace(" ", "").replace("-", "").replace("+", "")
        if (cleanNumber.isEmpty()) return

        try {
            val encodedMsg = Uri.encode(initialMessage)
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMsg")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            BridgeStateManager.logEvent("Discussion WhatsApp ouverte vers $cleanNumber", EventType.INFO)
            BluetoothService.instance?.sendPacket(BSBPacket("WHATSAPP_CHAT_OPENED", listOf(cleanNumber)))
        } catch (e: Exception) {
            Log.e(TAG, "Erreur ouverture chat WhatsApp", e)
            BridgeStateManager.logEvent("Échec ouverture WhatsApp: ${e.message}", EventType.ERROR)
        }
    }
}
