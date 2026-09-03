package com.hamza.blackberrybridge.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket

class BatteryBridgeManager(private val service: BluetoothService) {
    private val TAG = "BatteryManager"
    private var lastReportedLevel = -1
    private val threshold = 5 // Only report if changed by 5%

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            
            if (level != -1 && scale != -1) {
                val batteryPct = (level * 100) / scale.toFloat()
                val currentPct = batteryPct.toInt()
                
                // Avoid spamming Bluetooth. Only report significant changes.
                if (lastReportedLevel == -1 || Math.abs(currentPct - lastReportedLevel) >= threshold) {
                    lastReportedLevel = currentPct
                    service.sendPacket(BSBPacket("PHONE_BATTERY", listOf(currentPct.toString())))
                    Log.d(TAG, "Sent battery update: $currentPct%")
                }
            }
        }
    }

    fun startMonitoring() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        service.registerReceiver(batteryReceiver, filter)
    }

    fun stopMonitoring() {
        try {
            service.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering battery receiver", e)
        }
    }
}
