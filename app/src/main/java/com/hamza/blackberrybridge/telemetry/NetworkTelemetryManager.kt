package com.hamza.blackberrybridge.telemetry

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.state.NetworkTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NetworkTelemetryManager(private val service: BluetoothService) {
    companion object {
        private const val TAG = "NetworkTelemetry"
        private const val TELEMETRY_INTERVAL_MS = 30_000L // Transmit every 30 seconds
    }

    private val telephonyManager = service.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val connectivityManager = service.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val scope = CoroutineScope(Dispatchers.IO)
    private var periodicJob: Job? = null
    private var currentSignalBars: Int = 0

    // Listener reference for API 31+
    private var telephonyCallback: Any? = null
    // Listener reference for API < 31
    private var phoneStateListener: PhoneStateListener? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            updateAndSendTelemetry(immediate = true)
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            updateAndSendTelemetry(immediate = true)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            updateAndSendTelemetry(immediate = false)
        }
    }

    fun startMonitoring() {
        registerNetworkCallback()
        registerSignalStrengthListener()
        startPeriodicTelemetry()
        updateAndSendTelemetry(immediate = true)
    }

    fun stopMonitoring() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
        unregisterSignalStrengthListener()
        periodicJob?.cancel()
        periodicJob = null
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
        }
    }

    private fun registerSignalStrengthListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                        currentSignalBars = signalStrength.level
                        updateAndSendTelemetry(immediate = false)
                    }
                }
                telephonyManager?.registerTelephonyCallback(service.mainExecutor, callback)
                telephonyCallback = callback
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                        super.onSignalStrengthsChanged(signalStrength)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            currentSignalBars = signalStrength?.level ?: 0
                        }
                        updateAndSendTelemetry(immediate = false)
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager?.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
                phoneStateListener = listener
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission to listen for cellular signal strength: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering signal listener", e)
        }
    }

    private fun unregisterSignalStrengthListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let {
                    telephonyManager?.unregisterTelephonyCallback(it)
                }
                telephonyCallback = null
            } else {
                @Suppress("DEPRECATION")
                phoneStateListener?.let {
                    telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
                }
                phoneStateListener = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering signal listener", e)
        }
    }

    private fun startPeriodicTelemetry() {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                delay(TELEMETRY_INTERVAL_MS)
                if (BridgeStateManager.isConnected.value) {
                    sendTelemetry()
                }
            }
        }
    }

    fun updateAndSendTelemetry(immediate: Boolean = false) {
        val telemetry = fetchCurrentTelemetry()
        BridgeStateManager.updateNetworkTelemetry(telemetry)

        if (immediate && BridgeStateManager.isConnected.value) {
            sendTelemetry(telemetry)
        }
    }

    fun sendImmediateTelemetry() {
        val telemetry = fetchCurrentTelemetry()
        BridgeStateManager.updateNetworkTelemetry(telemetry)
        sendTelemetry(telemetry)
    }

    private fun sendTelemetry(telemetry: NetworkTelemetry = fetchCurrentTelemetry()) {
        val statusStr = if (telemetry.isConnected) "ONLINE" else "OFFLINE"

        // Primary packet: CELL_TELEMETRY|Carrier|NetworkType|SignalBars|Status
        val packet = BSBPacket(
            "CELL_TELEMETRY",
            listOf(telemetry.carrier, telemetry.networkType, telemetry.signalBars.toString(), statusStr)
        )
        service.sendPacket(packet)

        // Compatibility alias: NETWORK_STATUS|Carrier|NetworkType|SignalBars|Status
        service.sendPacket(
            BSBPacket(
                "NETWORK_STATUS",
                listOf(telemetry.carrier, telemetry.networkType, telemetry.signalBars.toString(), statusStr)
            )
        )

        BridgeStateManager.logEvent(
            "📡 Télémesure: ${telemetry.carrier} (${telemetry.networkType}) $statusStr - Signal: ${telemetry.signalBars}/4",
            com.hamza.blackberrybridge.state.EventType.INFO
        )
        Log.d(TAG, "Sent cellular telemetry to BlackBerry: ${telemetry.carrier} ${telemetry.networkType} ${telemetry.signalBars}/4 $statusStr")
    }

    private fun fetchCurrentTelemetry(): NetworkTelemetry {
        val activeNetwork = connectivityManager?.activeNetwork
        val caps = connectivityManager?.getNetworkCapabilities(activeNetwork)
        val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
            else -> "NONE"
        }

        // Carrier Name
        val carrier = try {
            val netOp = telephonyManager?.networkOperatorName
            val simOp = telephonyManager?.simOperatorName
            when {
                !netOp.isNullOrBlank() -> netOp
                !simOp.isNullOrBlank() -> simOp
                transport == "WIFI" -> "Wi-Fi Connecté"
                else -> "Aucun réseau"
            }
        } catch (e: Exception) {
            "Réseau inconnu"
        }

        // Signal strength
        val signalLevel = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val level = telephonyManager?.signalStrength?.level ?: -1
                if (level >= 0) level else currentSignalBars
            } else {
                currentSignalBars
            }
        } catch (e: Exception) {
            currentSignalBars
        }

        // Network generation / type
        val networkType = if (transport == "WIFI") {
            "WIFI"
        } else if (transport == "CELLULAR") {
            try {
                when (telephonyManager?.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_HSUPA,
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_GPRS,
                    TelephonyManager.NETWORK_TYPE_CDMA -> "2G"
                    else -> "4G"
                }
            } catch (e: SecurityException) {
                "CELL"
            } catch (e: Exception) {
                "CELL"
            }
        } else {
            "NONE"
        }

        return NetworkTelemetry(
            carrier = carrier,
            networkType = networkType,
            signalBars = signalLevel.coerceIn(0, 4),
            isConnected = isOnline,
            transport = transport
        )
    }
}
