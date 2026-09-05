package com.hamza.blackberrybridge.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.hamza.blackberrybridge.protocol.BSBPacket
import com.hamza.blackberrybridge.protocol.CommandDispatcher
import com.hamza.blackberrybridge.protocol.CommandParser
import com.hamza.blackberrybridge.state.BridgeStateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.UUID

@SuppressLint("MissingPermission")
class BBBluetoothManager(private val context: Context) {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val TAG = "BBBluetoothManager"
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private var activeSocket: BluetoothSocket? = null
    private var outWriter: PrintWriter? = null
    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    
    
    
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val current = BridgeStateManager.discoveredDevices.value.toMutableList()
                        if (!current.any { d -> d.address == it.address }) {
                            current.add(it)
                            BridgeStateManager.updateDiscoveredDevices(current)
                        }
                    }
                }
            }
        }
    }
    
    fun startScanning() {
        if (adapter == null || !adapter.isEnabled) return
        
        // Add paired devices first
        val paired = adapter.bondedDevices?.toList() ?: emptyList()
        BridgeStateManager.updateDiscoveredDevices(paired)
        val current = paired.toMutableList()
        
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)
        
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        adapter.startDiscovery()
        Log.d(TAG, "Started discovery...")
    }
    
    fun stopScanning() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Already unregistered
        }
        if (adapter?.isDiscovering == true) {
            adapter.cancelDiscovery()
        }
    }
    
    fun connectToDevice(device: BluetoothDevice, service: BluetoothService) {
        stopScanning()
        connectionJob?.cancel()
        
        connectionJob = scope.launch {
            try {
                Log.d(TAG, "Connecting to ${device.name}")
                withContext(Dispatchers.Main) {
                    service.updateNotification("Connexion à ${device.name}...")
                }
                
                // Fallback for SPP connections if standard UUID fails on some BB devices
                val socket = try {
                    device.createRfcommSocketToServiceRecord(SPP_UUID)
                } catch (e: Exception) {
                    device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket
                }
                
                socket.connect()
                manageConnectedSocket(socket, service)
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                withContext(Dispatchers.Main) {
                    service.updateNotification("○ Échec de connexion")
                    BridgeStateManager.setConnected(false, null)
                }
            }
        }
    }
    
    private suspend fun manageConnectedSocket(socket: BluetoothSocket, service: BluetoothService) {
        activeSocket = socket
        
        val deviceName = try { socket.remoteDevice.name } catch (e: SecurityException) { "Unknown" }
        
        withContext(Dispatchers.Main) {
            service.updateNotification("● BlackBerry connecté ($deviceName)")
            BridgeStateManager.setConnected(true, deviceName)
        }
        
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            outWriter = PrintWriter(OutputStreamWriter(socket.outputStream), true)
            
            // Handshake
            sendMessage("HELLO|BSB/1|ANDROID_DEVICE\n")
            
            while (currentCoroutineContext().isActive && socket.isConnected) {
                val line = reader.readLine() ?: break
                Log.d(TAG, "Received: $line")
                
                val packet = CommandParser.parse(line)
                if (packet != null) {
                    if (CommandParser.isCommandAllowed(packet)) {
                        CommandDispatcher.dispatch(service, packet)
                    } else {
                        sendMessage("ERROR|UNKNOWN_COMMAND\n")
                    }
                } else {
                    sendMessage("ERROR|INVALID_PACKET\n")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection lost", e)
        } finally {
            socket.close()
            activeSocket = null
            outWriter = null
            withContext(Dispatchers.Main) {
                service.updateNotification("○ BlackBerry déconnecté")
                BridgeStateManager.setConnected(false, null)
            }
        }
    }
    
    fun sendMessage(message: String) {
        scope.launch {
            try {
                outWriter?.print(message)
                outWriter?.flush()
                Log.d(TAG, "Sent: $message")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
            }
        }
    }
    
    fun disconnect() {
        connectionJob?.cancel()
        try {
            activeSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket", e)
        }
    }
}
