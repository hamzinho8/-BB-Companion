with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BluetoothService.kt', 'r') as f:
    content = f.read()

# Add ALERT_CHANNEL_ID and ALERT_NOTIFICATION_ID
content = content.replace('const val CHANNEL_ID = "bridge_channel"', 'const val CHANNEL_ID = "bridge_channel"\n        const val ALERT_CHANNEL_ID = "bridge_alert_channel"\n        const val ALERT_NOTIFICATION_ID = 2')

# Update createNotificationChannel
new_channels = """
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
"""

content = content.replace("""    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BlackBerry Smart Bridge",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }""", new_channels)

# Add showDisconnectionAlert
alert_method = """
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
"""

content = content.replace('fun updateNotification', alert_method + '\n    fun updateNotification')

with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BluetoothService.kt', 'w') as f:
    f.write(content)
