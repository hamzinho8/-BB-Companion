package com.hamza.blackberrybridge.calls

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.state.EventType
import com.hamza.blackberrybridge.telephony.SimManager

object CallController {
    private const val TAG = "CallController"

    @SuppressLint("MissingPermission")
    fun answerCall(context: Context, callId: String = "") {
        // Try Telecom InCallService first if active
        if (BridgeInCallService.activeCall != null) {
            BridgeInCallService.answerCall()
            SimManager.applyCallAudioRoute(context)
            BridgeStateManager.logEvent("Appel décroché via InCallService", EventType.SUCCESS)
            (context as? BluetoothService)?.sendPacket(BSBPacket("CALL_ANSWER_OK", listOf(callId)))
            return
        }

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        if (telecomManager == null) {
            sendError(context, callId, "FAILED", "TELECOM_SERVICE_UNAVAILABLE")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    telecomManager.acceptRingingCall()
                    Log.d(TAG, "Call answered successfully")
                    BridgeStateManager.logEvent("Appel décroché depuis BlackBerry", EventType.SUCCESS)
                    
                    // Appliquer la configuration audio (Haut-parleur ou Bluetooth SCO)
                    SimManager.applyCallAudioRoute(context)
                    
                    (context as? BluetoothService)?.sendPacket(BSBPacket("CALL_ANSWER_OK", listOf(callId)))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to answer call", e)
                    sendError(context, callId, "FAILED", e.message ?: "EXCEPTION")
                }
            } else {
                Log.w(TAG, "Missing ANSWER_PHONE_CALLS permission")
                sendError(context, callId, "FAILED", "RESTRICTED_PERMISSION_MISSING")
            }
        } else {
            sendError(context, callId, "FAILED", "RESTRICTED_API_LEVEL")
        }
    }

    @SuppressLint("MissingPermission")
    fun rejectCall(context: Context, callId: String = "") {
        if (BridgeInCallService.activeCall != null) {
            BridgeInCallService.endCall()
            BridgeStateManager.logEvent("Appel rejeté via InCallService", EventType.WARNING)
            (context as? BluetoothService)?.sendPacket(BSBPacket("CALL_REJECT_OK", listOf(callId)))
            return
        }

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        if (telecomManager == null) {
            sendError(context, callId, "FAILED", "TELECOM_SERVICE_UNAVAILABLE")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    telecomManager.endCall()
                    Log.d(TAG, "Call rejected successfully")
                    BridgeStateManager.logEvent("Appel rejeté depuis BlackBerry", EventType.WARNING)
                    (context as? BluetoothService)?.sendPacket(BSBPacket("CALL_REJECT_OK", listOf(callId)))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reject call", e)
                    sendError(context, callId, "FAILED", e.message ?: "EXCEPTION")
                }
            } else {
                sendError(context, callId, "FAILED", "RESTRICTED_PERMISSION_MISSING")
            }
        } else {
            sendError(context, callId, "FAILED", "RESTRICTED_API_LEVEL")
        }
    }

    @SuppressLint("MissingPermission")
    fun endCall(context: Context) {
        if (BridgeInCallService.activeCall != null) {
            BridgeInCallService.endCall()
            BridgeStateManager.logEvent("Appel raccroché via InCallService", EventType.INFO)
            (context as? BluetoothService)?.sendPacket(BSBPacket("CALL_END", listOf("LOCAL")))
            return
        }

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    telecomManager.endCall()
                    Log.d(TAG, "Active call ended successfully")
                    BridgeStateManager.logEvent("Appel raccroché", EventType.INFO)
                    (context as? BluetoothService)?.sendPacket(BSBPacket("CALL_END", listOf("LOCAL")))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to end call", e)
                }
            }
        }
    }

    /**
     * Lance un appel sortant en prenant en compte la sélection Double SIM
     * et l'activation audio automatique (haut-parleur / kit mains-libres).
     */
    @SuppressLint("MissingPermission")
    fun makeCall(context: Context, rawPhoneNumber: String, requestedSlot: Int? = null) {
        val cleanNumber = rawPhoneNumber.trim().replace(" ", "").replace("-", "")
        if (cleanNumber.isEmpty()) {
            sendError(context, rawPhoneNumber, "FAILED", "EMPTY_PHONE_NUMBER")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission CALL_PHONE missing")
            BridgeStateManager.logEvent("Erreur: Permission CALL_PHONE non accordée", EventType.ERROR)
            sendError(context, cleanNumber, "FAILED", "CALL_PHONE_PERMISSION_MISSING")
            return
        }

        try {
            val targetSim = SimManager.resolveTargetSim(context, requestedSlot)
            val simName = targetSim?.displayName ?: "SIM 1"
            val slot = targetSim?.slotIndex ?: 0
            val subId = targetSim?.subscriptionId ?: (slot + 1)

            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val uri = Uri.parse("tel:${Uri.encode(cleanNumber)}")

            var placedViaTelecom = false

            // Essai via TelecomManager (API standard Android pour forcer le compte d'appel / SIM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && telecomManager != null && targetSim?.phoneAccountHandle != null) {
                try {
                    val extras = Bundle().apply {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, targetSim.phoneAccountHandle)
                    }
                    telecomManager.placeCall(uri, extras)
                    placedViaTelecom = true
                    Log.d(TAG, "Call placed via TelecomManager on $simName (Handle: ${targetSim.phoneAccountHandle})")
                } catch (e: Exception) {
                    Log.w(TAG, "TelecomManager.placeCall failed, fallback to Intent: ${e.message}")
                }
            }

            if (!placedViaTelecom) {
                // Lancement par Intent avec injection de tous les extras constructeurs Dual-SIM
                val intent = Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    
                    // Extra officiel Telecom
                    if (targetSim?.phoneAccountHandle != null) {
                        putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, targetSim.phoneAccountHandle)
                    }
                    
                    // Extras constructeurs pour compatibilité Dual-SIM (Xiaomi, Samsung, MediaTek, Qualcomm)
                    putExtra("com.android.phone.force.slot", true)
                    putExtra("Cdma_Supp", true)
                    putExtra("simSlot", slot)
                    putExtra("com.android.phone.extra.slot", slot)
                    putExtra("subscription", subId)
                    putExtra("phone_id", slot)
                    putExtra("slot", slot)
                    putExtra("slot_id", slot)
                    putExtra("sim_slot", slot)
                }
                context.startActivity(intent)
                Log.d(TAG, "Call placed via Intent ACTION_CALL on $simName (Slot $slot, Sub $subId)")
            }

            BridgeStateManager.logEvent("Appel lancé vers $cleanNumber [$simName]", EventType.SUCCESS)

            // Notifier le BlackBerry du succès et de la SIM utilisée
            val service = context as? BluetoothService ?: BluetoothService.instance
            service?.sendPacket(BSBPacket("CALL_OUTBOUND_OK", listOf(cleanNumber, simName, slot.toString())))

            // Activer la configuration audio (Haut-parleur mains-libres ou Bluetooth SCO)
            SimManager.applyCallAudioRoute(context)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call", e)
            BridgeStateManager.logEvent("Échec appel vers $cleanNumber: ${e.message}", EventType.ERROR)
            sendError(context, cleanNumber, "FAILED", e.message ?: "EXCEPTION")
        }
    }

    fun sendSimListToBlackBerry(service: BluetoothService) {
        val sims = SimManager.refreshSims(service)
        val args = mutableListOf<String>()
        args.add(sims.size.toString())
        for (sim in sims) {
            args.add(sim.displayName)
            args.add(sim.slotIndex.toString())
        }
        service.sendPacket(BSBPacket("SIM_LIST", args))
        Log.d(TAG, "Sent SIM list to BlackBerry: $args")
    }

    private fun sendError(context: Context, callId: String, status: String, reason: String) {
        val service = (context as? BluetoothService) ?: BluetoothService.instance
        service?.sendPacket(BSBPacket("CALL_RESULT", listOf(callId, status, reason)))
    }
}
