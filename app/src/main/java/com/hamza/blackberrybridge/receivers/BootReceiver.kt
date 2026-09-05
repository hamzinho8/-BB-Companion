package com.hamza.blackberrybridge.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, checking auto-start...")
            val dataStore = SettingsDataStore(context)
            CoroutineScope(Dispatchers.IO).launch {
                val autoConnect = dataStore.autoConnectFlow.first()
                if (autoConnect) {
                    val serviceIntent = Intent(context, BluetoothService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try { context.startForegroundService(serviceIntent) } catch (e: Exception) { Log.e("BootReceiver", "Failed to start service", e) }
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d("BootReceiver", "Service started on boot.")
                }
            }
        }
    }
}
