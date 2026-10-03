package com.hamza.blackberrybridge.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import com.hamza.blackberrybridge.R
import com.hamza.blackberrybridge.audio.AudioStreamManager
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.ui.MainActivity

/**
 * Widget de bureau Android façon "BlackBerry Bridge".
 *
 * Fonctionnalités :
 * 1. Silhouette stylisée du BlackBerry Curve 9300 avec éclairage d'état dynamique.
 * 2. Statut Bluetooth en temps réel (● Connecté / ○ Déconnecté).
 * 3. Niveau de batterie du BlackBerry Curve 9300.
 * 4. Bouton interactif ON/OFF pour basculer instantanément la diffusion audio 16 kHz HD.
 */
class BridgeWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "BridgeWidgetProvider"
        const val ACTION_TOGGLE_AUDIO = "com.hamza.blackberrybridge.widget.ACTION_TOGGLE_AUDIO"

        /**
         * Met à jour immédiatement tous les widgets installés sur l'écran d'accueil.
         */
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, BridgeWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds.isNotEmpty()) {
                    val intent = Intent(context, BridgeWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    }
                    context.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erreur mise à jour widget: ${e.message}")
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = buildRemoteViews(context)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_TOGGLE_AUDIO) {
            handleToggleAudio(context)
        }
    }

    private fun handleToggleAudio(context: Context) {
        val isAudioActive = AudioStreamManager.isStreamingFlow.value

        if (isAudioActive) {
            // Coupure immédiate du flux
            AudioStreamManager.stop(stopProjectionService = true)
            Toast.makeText(context, "⏹ Diffusion audio BlackBerry arrêtée", Toast.LENGTH_SHORT).show()
            updateAllWidgets(context)
        } else {
            // Activation du flux audio
            val isConnected = BridgeStateManager.isConnected.value
            val service = BluetoothService.instance

            if (!isConnected || service == null) {
                // Si déconnecté, ouvrir l'application pour connecter le BlackBerry
                Toast.makeText(context, "Veuillez connecter votre BlackBerry Curve 9300", Toast.LENGTH_SHORT).show()
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(mainIntent)
                return
            }

            // Si déjà autorisé MediaProjection, démarrer directement
            if (AudioStreamManager.activeMediaProjection != null) {
                AudioStreamManager.start(service)
                Toast.makeText(context, "▶ Diffusion 16kHz HD activée vers BlackBerry", Toast.LENGTH_SHORT).show()
                updateAllWidgets(context)
            } else {
                // Demande de permission capture d'écran / haut-parleurs via MainActivity
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_START_AUDIO_CAPTURE, true)
                }
                context.startActivity(mainIntent)
            }
        }
    }

    private fun buildRemoteViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_bridge)

        val isConnected = BridgeStateManager.isConnected.value
        val deviceName = BridgeStateManager.deviceName.value ?: "Curve 9300 3G"
        val battery = BridgeStateManager.batteryLevel.value
        val isAudioActive = AudioStreamManager.isStreamingFlow.value

        // 1. Statut Bluetooth
        if (isConnected) {
            views.setTextViewText(R.id.widget_bt_status, "● Connecté")
            views.setTextColor(R.id.widget_bt_status, Color.parseColor("#00E5FF")) // Cyan néon
        } else {
            views.setTextViewText(R.id.widget_bt_status, "○ Déconnecté")
            views.setTextColor(R.id.widget_bt_status, Color.parseColor("#94A3B8")) // Gris doux
        }

        // 2. Nom du modèle
        views.setTextViewText(R.id.widget_model_name, if (isConnected) deviceName else "BlackBerry Curve 9300")

        // 3. Badge Niveau de Batterie
        val batteryText = when {
            battery != null -> "🔋 $battery%"
            isConnected -> "🔋 --%"
            else -> "🔋 Inactif"
        }
        views.setTextViewText(R.id.widget_battery_badge, batteryText)

        // 4. Bouton ON/OFF pour basculer le son
        if (isAudioActive) {
            views.setInt(R.id.widget_btn_audio_toggle, "setBackgroundResource", R.drawable.widget_btn_audio_on)
            views.setTextViewText(R.id.widget_btn_audio_icon, "⏹")
            views.setTextColor(R.id.widget_btn_audio_icon, Color.parseColor("#FFFFFF"))
            views.setTextViewText(R.id.widget_btn_audio_text, "Audio Actif (16kHz HD)")
        } else {
            views.setInt(R.id.widget_btn_audio_toggle, "setBackgroundResource", R.drawable.widget_btn_audio_off)
            views.setTextViewText(R.id.widget_btn_audio_icon, "▶")
            views.setTextColor(R.id.widget_btn_audio_icon, Color.parseColor("#38BDF8"))
            views.setTextViewText(R.id.widget_btn_audio_text, "Diffuser le son (16kHz)")
        }

        // Intent pour ouvrir l'application lors du clic sur le corps du widget
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_silhouette_container, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_model_name, openAppPendingIntent)

        // Intent pour le basculement ON/OFF du son au clic sur le bouton
        val toggleAudioIntent = Intent(context, BridgeWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_AUDIO
        }
        val toggleAudioPendingIntent = PendingIntent.getBroadcast(
            context,
            200,
            toggleAudioIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_audio_toggle, toggleAudioPendingIntent)

        return views
    }
}
