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
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.hamza.blackberrybridge.R
import com.hamza.blackberrybridge.audio.AudioStreamManager
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.telephony.SimManager
import com.hamza.blackberrybridge.ui.MainActivity

/**
 * Widget de bureau Android « BlackBerry Bridge » Professionnel & Ultra-Robuste.
 *
 * Résout le problème "Impossible de charger le widget" (MIUI / HyperOS / Android) :
 * 1. Zéro balise non autorisée (aucun tag <View>, uniquement classes permises par RemoteViews).
 * 2. Zéro appel setInt("setBackgroundResource") qui déclenchait des exceptions de sécurité.
 * 3. Utilisation de conteneurs FrameLayout alternés avec setViewVisibility.
 *
 * Affiche instantanément toutes les informations vitales :
 * - État de connexion Bluetooth en direct (● Connecté / ○ Déconnecté)
 * - Pourcentage de batterie du BlackBerry Curve 9300
 * - Carte SIM active et opérateur réseau
 * - Canal audio téléphonique (SCO Bluetooth / Haut-Parleur)
 * - Statut de synchronisation instantanée (WhatsApp, SMS, Appels)
 * - Bouton interactif ON/OFF pour basculer la diffusion audio 16 kHz HD
 * - Bouton de rafraîchissement instantané d'un tap
 */
class BridgeWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "BridgeWidgetProvider"
        const val ACTION_TOGGLE_AUDIO = "com.hamza.blackberrybridge.widget.ACTION_TOGGLE_AUDIO"
        const val ACTION_REFRESH_WIDGET = "com.hamza.blackberrybridge.widget.ACTION_REFRESH_WIDGET"

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
                Log.w(TAG, "Erreur broadcast updateAllWidgets: ${e.message}")
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            try {
                val views = buildRemoteViews(context)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur onUpdate widget ID $appWidgetId", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_TOGGLE_AUDIO -> {
                handleToggleAudio(context)
            }
            ACTION_REFRESH_WIDGET -> {
                Toast.makeText(context, "🔄 Widget BlackBerry actualisé", Toast.LENGTH_SHORT).show()
                updateAllWidgets(context)
            }
        }
    }

    private fun handleToggleAudio(context: Context) {
        val isAudioActive = AudioStreamManager.isStreamingFlow.value

        if (isAudioActive) {
            // Coupure immédiate du flux
            AudioStreamManager.stop(stopProjectionService = true)
            Toast.makeText(context, "⏹ Diffusion audio arrêtée", Toast.LENGTH_SHORT).show()
            updateAllWidgets(context)
        } else {
            // Activation du flux audio
            val isConnected = BridgeStateManager.isConnected.value
            val service = BluetoothService.instance

            if (!isConnected || service == null) {
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
                Toast.makeText(context, "▶ Diffusion 16kHz HD activée", Toast.LENGTH_SHORT).show()
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

        try {
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

            // 2. Modèle de téléphone
            views.setTextViewText(R.id.widget_model_name, if (isConnected) deviceName else "BlackBerry Curve 9300")

            // 3. Badge Niveau de Batterie (Couleurs dynamiques selon niveau)
            val batteryText: String
            val batteryColor: Int
            when {
                battery != null -> {
                    batteryText = "🔋 $battery%"
                    batteryColor = when {
                        battery > 40 -> Color.parseColor("#34D399") // Vert émeraude
                        battery > 15 -> Color.parseColor("#FBBF24") // Jaune ambre
                        else -> Color.parseColor("#F87171") // Rouge alerte
                    }
                }
                isConnected -> {
                    batteryText = "🔋 --%"
                    batteryColor = Color.parseColor("#38BDF8")
                }
                else -> {
                    batteryText = "🔋 Inactif"
                    batteryColor = Color.parseColor("#64748B")
                }
            }
            views.setTextViewText(R.id.widget_battery_badge, batteryText)
            views.setTextColor(R.id.widget_battery_badge, batteryColor)

            // 4. Informations Téléphonie & SIM
            val sims = SimManager.availableSims.value
            val activeSim = sims.firstOrNull()
            val simLabel = if (activeSim != null) {
                "📶 ${activeSim.displayName} (${activeSim.carrierName})"
            } else {
                "📶 Réseau Télécom Prêt"
            }
            views.setTextViewText(R.id.widget_network_info, simLabel)

            // 5. Canal Audio d'Appel en cours
            val route = SimManager.audioRoute.value
            val routeLabel = when (route) {
                SimManager.AUDIO_BLUETOOTH -> "📞 SCO Actif"
                SimManager.AUDIO_SPEAKERPHONE -> "📢 HP Actif"
                else -> "📱 Écouteur"
            }
            views.setTextViewText(R.id.widget_call_route_info, routeLabel)

            // 6. Ligne de Synthèse Instantanée
            val isCallActive = com.hamza.blackberrybridge.calls.BridgeInCallService.activeCall != null
            val summaryText = when {
                isCallActive -> "📞 Appel téléphonique en cours"
                isAudioActive -> "🎵 Flux audio 16 kHz HD actif"
                isConnected -> "💬 WhatsApp, SMS & Clavier Prêts"
                else -> "Toucher pour ouvrir l'application"
            }
            views.setTextViewText(R.id.widget_status_summary, summaryText)

            // 7. Basculement d'état du bouton Audio (Safe RemoteViews via setViewVisibility)
            if (isAudioActive) {
                views.setViewVisibility(R.id.widget_btn_audio_on_layout, View.VISIBLE)
                views.setViewVisibility(R.id.widget_btn_audio_off_layout, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_btn_audio_on_layout, View.GONE)
                views.setViewVisibility(R.id.widget_btn_audio_off_layout, View.VISIBLE)
            }

            // 8. Intent pour ouvrir l'application (Clic sur Silhouette ou Titre)
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                100,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_phone_container, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_model_name, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_status_summary, openAppPendingIntent)

            // 9. Intent pour rafraîchir le widget instantanément (Clic sur 🔄)
            val refreshIntent = Intent(context, BridgeWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                150,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

            // 10. Intent pour basculer le flux audio (Clic sur le bouton)
            val toggleAudioIntent = Intent(context, BridgeWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_AUDIO
            }
            val toggleAudioPendingIntent = PendingIntent.getBroadcast(
                context,
                200,
                toggleAudioIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_audio_toggle_frame, toggleAudioPendingIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur construction RemoteViews", e)
        }

        return views
    }
}
