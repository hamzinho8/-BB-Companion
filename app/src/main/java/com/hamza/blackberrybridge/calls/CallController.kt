package com.hamza.blackberrybridge.calls

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket

object CallController {
    private const val TAG = "CallController"

    @SuppressLint("MissingPermission")
    fun answerCall(context: Context, callId: String) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    telecomManager.acceptRingingCall()
                    Log.d(TAG, "Call answered successfully")
                    
                    // Activer le haut-parleur automatiquement
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                            audioManager.mode = android.media.AudioManager.MODE_IN_CALL
                            audioManager.isSpeakerphoneOn = true
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to enable speakerphone", e)
                        }
                    }, 1500) // Délai pour laisser le temps à l'appel de s'établir
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to answer call", e)
                    sendError(context, callId, "FAILED", "EXCEPTION")
                }
            } else {
                sendError(context, callId, "FAILED", "RESTRICTED_PERMISSION_MISSING")
            }
        } else {
            sendError(context, callId, "FAILED", "RESTRICTED_API_LEVEL")
        }
    }

    @SuppressLint("MissingPermission")
    fun rejectCall(context: Context, callId: String) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    telecomManager.endCall()
                    Log.d(TAG, "Call rejected successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reject call", e)
                    sendError(context, callId, "FAILED", "EXCEPTION")
                }
            } else {
                sendError(context, callId, "FAILED", "RESTRICTED_PERMISSION_MISSING")
            }
        } else {
            sendError(context, callId, "FAILED", "RESTRICTED_API_LEVEL")
        }
    }

    @SuppressLint("MissingPermission")
    fun makeCall(context: Context, phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_CALL)
                intent.data = android.net.Uri.parse("tel:$phoneNumber")
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Initiated outbound call to $phoneNumber")
                
                // Optionnel : Activer le haut-parleur pour le kit mains-libres
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                        audioManager.mode = android.media.AudioManager.MODE_IN_CALL
                        audioManager.isSpeakerphoneOn = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to enable speakerphone for outbound call", e)
                    }
                }, 2000)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to make call", e)
                sendError(context, phoneNumber, "FAILED", "EXCEPTION")
            }
        } else {
            sendError(context, phoneNumber, "FAILED", "RESTRICTED_PERMISSION_MISSING")
        }
    }

    private fun sendError(context: Context, callId: String, status: String, reason: String) {
        (context as? BluetoothService)?.sendPacket(BSBPacket("CALL_RESULT", listOf(callId, status, reason)))
    }
}
