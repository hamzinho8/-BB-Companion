package com.hamza.blackberrybridge.apps

import android.content.Context
import android.content.Intent
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket

object AppLauncher {
    private const val TAG = "AppLauncher"

    fun launchApp(context: Context, packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Launched app: $packageName")
            } else {
                Log.w(TAG, "App not found: $packageName")
                (context as? BluetoothService)?.sendPacket(BSBPacket("ERROR", listOf("APP_NOT_FOUND")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app: $packageName", e)
            (context as? BluetoothService)?.sendPacket(BSBPacket("ERROR", listOf("EXCEPTION")))
        }
    }
}
