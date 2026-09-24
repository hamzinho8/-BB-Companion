package com.hamza.blackberrybridge.protocol

import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.calls.CallController
import com.hamza.blackberrybridge.media.MediaSessionController
import com.hamza.blackberrybridge.clipboard.ClipboardManagerBridge
import com.hamza.blackberrybridge.contacts.ContactManager
import com.hamza.blackberrybridge.apps.AppLauncher
import com.hamza.blackberrybridge.audio.SoundManager
import com.hamza.blackberrybridge.weather.WeatherManager
import com.hamza.blackberrybridge.voice.VoiceReplyManager
import com.hamza.blackberrybridge.state.BridgeStateManager

object CommandDispatcher {
    
    fun dispatch(service: BluetoothService, packet: BSBPacket) {
        when (packet.command) {
            "PING" -> service.sendPacket(BSBPacket("PONG", emptyList()))
            "HELLO", "READY" -> {
                // Handshake ready, sync SIM information and telemetry
                CallController.sendSimListToBlackBerry(service)
                service.telemetryManager?.sendImmediateTelemetry()
            }
            "BATTERY" -> {
                if (packet.args.isNotEmpty()) {
                    packet.args[0].toIntOrNull()?.let { BridgeStateManager.setBatteryLevel(it) }
                }
            }
            "GET_PHONE_BATTERY" -> {
                val batteryManager = service.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
                val batteryPct = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                service.sendPacket(BSBPacket("PHONE_BATTERY", listOf(batteryPct.toString())))
            }
            "CALL_ANSWER" -> {
                val callId = if (packet.args.isNotEmpty()) packet.args[0] else ""
                CallController.answerCall(service, callId)
            }
            "CALL_REJECT" -> {
                val callId = if (packet.args.isNotEmpty()) packet.args[0] else ""
                CallController.rejectCall(service, callId)
            }
            "CALL_END", "CALL_HANGUP" -> {
                CallController.endCall(service)
            }
            "CALL_OUTBOUND", "CALL", "DIAL" -> {
                if (packet.args.isNotEmpty()) {
                    val number = packet.args[0]
                    val requestedSlot = if (packet.args.size > 1) packet.args[1].toIntOrNull() else null
                    CallController.makeCall(service, number, requestedSlot)
                }
            }
            "GET_SIMS" -> {
                CallController.sendSimListToBlackBerry(service)
            }
            "SET_DEFAULT_SIM" -> {
                if (packet.args.isNotEmpty()) {
                    packet.args[0].toIntOrNull()?.let { slot ->
                        com.hamza.blackberrybridge.telephony.SimManager.setPreferredSlot(service, slot)
                        service.sendPacket(BSBPacket("DEFAULT_SIM_SET", listOf(slot.toString())))
                    }
                }
            }
            "SPEAKER_TOGGLE" -> {
                com.hamza.blackberrybridge.telephony.SimManager.toggleSpeakerphone(service)
            }
            "SPEAKER_ON" -> {
                com.hamza.blackberrybridge.telephony.SimManager.setAudioRoute(service, com.hamza.blackberrybridge.telephony.SimManager.AUDIO_SPEAKERPHONE)
                com.hamza.blackberrybridge.telephony.SimManager.applyCallAudioRoute(service)
                service.sendPacket(BSBPacket("SPEAKER_STATUS", listOf("ON")))
                BridgeStateManager.logEvent("Haut-parleur forcé: ON", com.hamza.blackberrybridge.state.EventType.INFO)
            }
            "SPEAKER_OFF" -> {
                com.hamza.blackberrybridge.telephony.SimManager.setAudioRoute(service, com.hamza.blackberrybridge.telephony.SimManager.AUDIO_EARPIECE)
                com.hamza.blackberrybridge.telephony.SimManager.applyCallAudioRoute(service)
                service.sendPacket(BSBPacket("SPEAKER_STATUS", listOf("OFF")))
                BridgeStateManager.logEvent("Haut-parleur forcé: OFF", com.hamza.blackberrybridge.state.EventType.INFO)
            }
            "AUDIO_ROUTE" -> {
                if (packet.args.isNotEmpty()) {
                    val requestedRoute = packet.args[0]
                    com.hamza.blackberrybridge.telephony.SimManager.setAudioRoute(service, requestedRoute)
                    com.hamza.blackberrybridge.telephony.SimManager.applyCallAudioRoute(service)
                    service.sendPacket(BSBPacket("AUDIO_ROUTE_OK", listOf(requestedRoute)))
                }
            }
            "VOICE_RX" -> {
                if (packet.args.isNotEmpty()) {
                    com.hamza.blackberrybridge.audio.CallAudioBridge.playIncomingVoice(packet.args[0])
                }
            }
            "VOICE_BRIDGE_START" -> {
                com.hamza.blackberrybridge.audio.CallAudioBridge.startStreaming(service)
                service.sendPacket(BSBPacket("VOICE_BRIDGE_STATUS", listOf("ACTIVE")))
            }
            "VOICE_BRIDGE_STOP" -> {
                com.hamza.blackberrybridge.audio.CallAudioBridge.stopStreaming()
                service.sendPacket(BSBPacket("VOICE_BRIDGE_STATUS", listOf("STOPPED")))
            }
            "MEDIA_PLAY", "MEDIA_PAUSE", "MEDIA_NEXT", "MEDIA_PREVIOUS" -> {
                MediaSessionController.dispatchMediaCommand(service, packet.command)
                BridgeStateManager.logEvent("Contrôle Média: ${packet.command}", com.hamza.blackberrybridge.state.EventType.INFO)
            }
            "CLIPBOARD" -> {
                if (packet.args.isNotEmpty()) ClipboardManagerBridge.copyToAndroidClipboard(service, packet.args[0])
            }
            "CONTACT_SEARCH" -> {
                val query = if (packet.args.isNotEmpty()) packet.args[0] else ""
                ContactManager.searchContacts(service, query)
            }
            "SYNC_CONTACTS", "GET_CONTACTS", "GET_VIP" -> {
                com.hamza.blackberrybridge.contacts.VipContactManager.syncVipContactsToBlackBerry(service)
            }
            "OPEN_APP" -> {
                if (packet.args.isNotEmpty()) AppLauncher.launchApp(service, packet.args[0])
            }
            "FIND_PHONE" -> {
                SoundManager.findPhone(service)
            }
            "FIND_PHONE_STOP" -> {
                SoundManager.stopFindPhone(service, notifyBlackBerry = false)
            }
            "GET_NETWORK", "GET_CELL_INFO", "TELEMETRY" -> {
                service.telemetryManager?.sendImmediateTelemetry()
            }
            "WEATHER" -> {
                WeatherManager.fetchWeather(service)
            }
            "REPLY_MSG" -> {
                if (packet.args.size >= 2) {
                    val notifId = packet.args[0]
                    val messageText = packet.args[1]
                    com.hamza.blackberrybridge.notification.BridgeNotificationListener.instance?.replyToMessage(notifId, messageText)
                }
            }
            "VOICE_REPLY" -> {
                if (packet.args.size >= 2) {
                    val notifId = packet.args[0]
                    val base64 = packet.args[1]
                    VoiceReplyManager.handleVoiceReply(service, notifId, base64)
                }
            }
        }
    }
}
