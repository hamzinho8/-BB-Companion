package com.hamza.blackberrybridge.state

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BridgeEvent(
    val time: String,
    val description: String,
    val type: EventType
)

enum class EventType {
    INFO, SUCCESS, WARNING, ERROR
}

object BridgeStateManager {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<BridgeEvent>>(emptyList())
    val recentEvents: StateFlow<List<BridgeEvent>> = _recentEvents.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun setConnected(connected: Boolean, name: String? = null) {
        if (_isConnected.value != connected) {
            logEvent(if (connected) "Connecté à ${name ?: "BlackBerry"}" else "Déconnecté", if (connected) EventType.SUCCESS else EventType.WARNING)
        }
        _isConnected.value = connected
        _deviceName.value = name
        if (!connected) _batteryLevel.value = null
    }

    fun updateDiscoveredDevices(devices: List<BluetoothDevice>) {
        _discoveredDevices.value = devices
    }

    fun setBatteryLevel(level: Int) {
        _batteryLevel.value = level
    }

    fun setServiceRunning(isRunning: Boolean) {
        if (_isServiceRunning.value != isRunning) {
            logEvent(if (isRunning) "Service démarré" else "Service arrêté", EventType.INFO)
        }
        _isServiceRunning.value = isRunning
    }

    fun logEvent(description: String, type: EventType = EventType.INFO) {
        val newEvent = BridgeEvent(timeFormat.format(Date()), description, type)
        val current = _recentEvents.value.toMutableList()
        current.add(0, newEvent)
        if (current.size > 5) {
            current.removeAt(current.size - 1)
        }
        _recentEvents.value = current
    }
}
