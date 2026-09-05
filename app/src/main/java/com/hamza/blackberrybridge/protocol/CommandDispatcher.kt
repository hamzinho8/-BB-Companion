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
                // Handshake ready, can trigger initial sync
            }
            "BATTERY", "PHONE_BATTERY" -> {
                if (packet.args.isNotEmpty()) {
                    packet.args[0].toIntOrNull()?.let { BridgeStateManager.setBatteryLevel(it) }
                }
            }
            "CALL_ANSWER" -> {
                if (packet.args.isNotEmpty()) CallController.answerCall(service, packet.args[0])
                    BridgeStateManager.logEvent("Appel répondu", com.hamza.blackberrybridge.state.EventType.SUCCESS)
            }
            "CALL_REJECT" -> {
                if (packet.args.isNotEmpty()) CallController.rejectCall(service, packet.args[0])
                    BridgeStateManager.logEvent("Appel rejeté", com.hamza.blackberrybridge.state.EventType.WARNING)
            }
            "MEDIA_PLAY", "MEDIA_PAUSE", "MEDIA_NEXT", "MEDIA_PREVIOUS" -> {
                MediaSessionController.dispatchMediaCommand(service, packet.command)
                BridgeStateManager.logEvent("Contrôle Média: ${packet.command}", com.hamza.blackberrybridge.state.EventType.INFO)
            }
            "CLIPBOARD" -> {
                if (packet.args.isNotEmpty()) ClipboardManagerBridge.copyToAndroidClipboard(service, packet.args[0])
            }
            "CONTACT_SEARCH" -> {
                if (packet.args.isNotEmpty()) ContactManager.searchContacts(service, packet.args[0])
            }
            "OPEN_APP" -> {
                if (packet.args.isNotEmpty()) AppLauncher.launchApp(service, packet.args[0])
            }
            "FIND_PHONE" -> {
                SoundManager.findPhone(service)
            }
            "FIND_PHONE_STOP" -> {
                SoundManager.stopFindPhone()
            }
            "WEATHER" -> {
                WeatherManager.fetchWeather(service)
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
