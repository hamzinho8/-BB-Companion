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

    private fun sendError(context: Context, callId: String, status: String, reason: String) {
        (context as? BluetoothService)?.sendPacket(BSBPacket("CALL_RESULT", listOf(callId, status, reason)))
    }
}
