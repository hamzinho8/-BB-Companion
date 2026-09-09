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
        const val ALERT_CHANNEL_ID = "bridge_alert_channel"
        const val ALERT_NOTIFICATION_ID = 2
        const val NOTIFICATION_ID = 1
        var instance: BluetoothService? = null
            private set
    }

    var bluetoothManager: BBBluetoothManager? = null
        private set

    private var batteryManager: com.hamza.blackberrybridge.battery.BatteryBridgeManager? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.hamza.blackberrybridge.state.BridgeStateManager.setServiceRunning(true)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("BlackBerry déconnecté"))
        
        bluetoothManager = BBBluetoothManager(this)
        
        batteryManager = com.hamza.blackberrybridge.battery.BatteryBridgeManager(this)
        batteryManager?.startMonitoring()
        com.hamza.blackberrybridge.media.MediaSessionController.startListening(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager?.disconnect()
        batteryManager?.stopMonitoring()
        com.hamza.blackberrybridge.media.MediaSessionController.stopListening(this)
        instance = null
        com.hamza.blackberrybridge.state.BridgeStateManager.setServiceRunning(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BlackBerry Smart Bridge",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
            
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Alertes de Déconnexion",
                NotificationManager.IMPORTANCE_HIGH
            )
            alertChannel.description = "Avertissements lorsque la connexion avec le BlackBerry est perdue"
            alertChannel.enableVibration(true)
            manager.createNotificationChannel(alertChannel)
        }
    }


    
    fun showDisconnectionAlert(deviceName: String?) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val name = deviceName ?: "Votre BlackBerry"
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("⚠️ Connexion Perdue")
            .setContentText("$name s'est déconnecté inopinément.")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
            
        manager.notify(ALERT_NOTIFICATION_ID, notification)
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
        bluetoothManager?.sendMessage(packet.toString())
    }
}
