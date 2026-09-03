package com.hamza.blackberrybridge.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.hamza.blackberrybridge.protocol.BSBPacket
import com.hamza.blackberrybridge.protocol.CommandParser
import com.hamza.blackberrybridge.protocol.CommandDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.UUID

class BluetoothServer(private val service: BluetoothService) {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val TAG = "BluetoothServer"

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var outWriter: PrintWriter? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun startServer() {
        val bluetoothManager = service.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth not enabled")
            return
        }

        scope.launch {
            listenForConnections(adapter)
        }
    }

    private suspend fun listenForConnections(adapter: BluetoothAdapter) {
        var backoffDelay = 2000L
        val maxDelay = 30000L

        while (currentCoroutineContext().isActive) {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("BBCompanion", SPP_UUID)
                Log.d(TAG, "Waiting for connection...")
                
                val socket = serverSocket?.accept()
                if (socket != null) {
                    val deviceName = try { socket.remoteDevice.name } catch (e: SecurityException) { "Unknown" }
                    Log.d(TAG, "Connected to $deviceName")
                    backoffDelay = 2000L // Reset delay on successful connection
                    serverSocket?.close() // Only one connection at a time
                    manageConnectedSocket(socket)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing Bluetooth permissions", e)
                delay(backoffDelay)
                backoffDelay = (backoffDelay * 2).coerceAtMost(maxDelay)
            } catch (e: IOException) {
                Log.e(TAG, "Server socket failed. Retrying in ${backoffDelay}ms...", e)
                delay(backoffDelay)
                backoffDelay = (backoffDelay * 2).coerceAtMost(maxDelay)
            }
        }
    }

    private suspend fun manageConnectedSocket(socket: BluetoothSocket) {
        activeSocket = socket
        
        withContext(Dispatchers.Main) {
            try {
                service.updateNotification("● BlackBerry connecté (${socket.remoteDevice.name})")
            } catch (e: SecurityException) {
                service.updateNotification("● BlackBerry connecté")
            }
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

    fun stopServer() {
        scope.cancel()
        try {
            serverSocket?.close()
            activeSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing sockets", e)
        }
    }
}
