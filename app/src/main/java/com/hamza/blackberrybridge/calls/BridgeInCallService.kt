package com.hamza.blackberrybridge.calls

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.state.EventType
import com.hamza.blackberrybridge.telephony.SimManager

/**
 * Telecom InCallService companion for BlackBerry Bridge.
 * Allows direct hardware-level audio routing (Loudspeaker, Bluetooth SCO, Earpiece)
 * and native call control (answer, reject, hangup) directly from Telecom subsystem.
 */
class BridgeInCallService : InCallService() {

    companion object {
        private const val TAG = "BridgeInCallService"

        var instance: BridgeInCallService? = null
            private set

        var activeCall: Call? = null
            private set

        /**
         * Routes call audio at Telecom system level
         */
        fun applyAudioRoute(routeType: String): Boolean {
            val service = instance ?: return false
            val targetRoute = when (routeType) {
                SimManager.AUDIO_SPEAKERPHONE -> CallAudioState.ROUTE_SPEAKER
                SimManager.AUDIO_BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
                else -> CallAudioState.ROUTE_EARPIECE
            }

            return try {
                service.setAudioRoute(targetRoute)
                Log.d(TAG, "Successfully applied Telecom audio route: $targetRoute ($routeType)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply Telecom audio route", e)
                false
            }
        }

        fun answerCall() {
            try {
                activeCall?.answer(0)
                Log.d(TAG, "InCallService: answerCall() executed")
            } catch (e: Exception) {
                Log.e(TAG, "InCallService: Error answering call", e)
            }
        }

        fun endCall() {
            try {
                activeCall?.disconnect()
                Log.d(TAG, "InCallService: endCall() executed")
            } catch (e: Exception) {
                Log.e(TAG, "InCallService: Error ending call", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "BridgeInCallService started and ready for Telecom audio routing")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        activeCall = null
        Log.d(TAG, "BridgeInCallService destroyed")
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCall = call
        Log.d(TAG, "onCallAdded: call=$call")

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                super.onStateChanged(call, state)
                Log.d(TAG, "Telecom call state changed: $state")
                when (state) {
                    Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                        // Enforce configured audio route when call connects
                        applyAudioRoute(SimManager.audioRoute.value)
                    }
                    Call.STATE_DISCONNECTED -> {
                        BluetoothService.instance?.sendPacket(BSBPacket("CALL_END", listOf("DISCONNECTED")))
                    }
                }
            }
        })

        // Apply audio route immediately on call creation
        applyAudioRoute(SimManager.audioRoute.value)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (activeCall == call) {
            activeCall = null
        }
        Log.d(TAG, "onCallRemoved: call=$call")
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        Log.d(TAG, "onCallAudioStateChanged: route=${audioState.route}")

        val routeName = when (audioState.route) {
            CallAudioState.ROUTE_SPEAKER -> "SPEAKERPHONE"
            CallAudioState.ROUTE_BLUETOOTH -> "BLUETOOTH"
            else -> "EARPIECE"
        }

        BluetoothService.instance?.sendPacket(BSBPacket("AUDIO_STATUS", listOf(routeName)))
        BridgeStateManager.logEvent("Route Audio Active: $routeName", EventType.INFO)
    }
}
