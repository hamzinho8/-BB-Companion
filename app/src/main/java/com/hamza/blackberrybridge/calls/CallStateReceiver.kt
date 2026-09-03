package com.hamza.blackberrybridge.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket

class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Unknown"
            
            val callId = "call_${System.currentTimeMillis()}"

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    BluetoothService.instance?.sendPacket(BSBPacket("CALL_INCOMING", listOf(callId, "Caller", incomingNumber)))
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    BluetoothService.instance?.sendPacket(BSBPacket("CALL_ACTIVE", listOf(callId)))
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    BluetoothService.instance?.sendPacket(BSBPacket("CALL_END", listOf(callId)))
                }
            }
        }
    }
}
