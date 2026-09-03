package com.hamza.blackberrybridge.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hamza.blackberrybridge.ui.MainActivity

class BluetoothService : Service() {

    companion object {
        const val CHANNEL_ID = "bridge_channel"
        const val NOTIFICATION_ID = 1
        var instance: BluetoothService? = null
            private set
    }

    private var server: BluetoothServer? = null

    private var batteryManager: com.hamza.blackberrybridge.battery.BatteryBridgeManager? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("BlackBerry déconnecté"))
        
        server = BluetoothServer(this)
        server?.startServer()
        
        batteryManager = com.hamza.blackberrybridge.battery.BatteryBridgeManager(this)
        batteryManager?.startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stopServer()
        batteryManager?.stopMonitoring()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BlackBerry Smart Bridge",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BlackBerrySmartBridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    fun sendPacket(packet: com.hamza.blackberrybridge.protocol.BSBPacket) {
        server?.sendMessage(packet.toString())
    }
}
