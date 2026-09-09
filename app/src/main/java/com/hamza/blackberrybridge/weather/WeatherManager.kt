package com.hamza.blackberrybridge.weather

import android.content.Context
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object WeatherManager {
    private const val TAG = "WeatherManager"
    
    // STATUS: SUPPORTED
    // Reason: Using Open-Meteo free API to fetch data without needing API keys.
    // Location is hardcoded to Casablanca as per user example for simplicity, 
    // but in a real app would use LocationManager (requires extra permissions).
    
    fun fetchWeather(context: Context) {
        GlobalScope.launch(Dispatchers.IO) {
            val service = context as? BluetoothService ?: return@launch
            
            try {
                // Casablanca coordinates: 33.5731, -7.5898
                val urlString = "https://api.open-meteo.com/v1/forecast?latitude=33.5731&longitude=-7.5898&current_weather=true"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    val current = json.getJSONObject("current_weather")
                    val temp = current.getDouble("temperature").toInt()
                    val weatherCode = current.getInt("weathercode")
                    
                    val condition = getWeatherCondition(weatherCode)
                    
                    com.hamza.blackberrybridge.state.BridgeStateManager.logEvent("Météo récupérée: $temp°C, $condition", com.hamza.blackberrybridge.state.EventType.INFO)
                    // FORMAT: WEATHER|22|C|Soleil|Casablanca
                    service.sendPacket(BSBPacket("WEATHER", listOf(temp.toString(), "C", condition, "Casablanca")))
                } else {
                    Log.e(TAG, "Weather API error: ${connection.responseCode}")
                    service.sendPacket(BSBPacket("ERROR", listOf("WEATHER_UNAVAILABLE")))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching weather", e)
                service.sendPacket(BSBPacket("ERROR", listOf("WEATHER_EXCEPTION")))
            }
        }
    }

    // WMO Weather interpretation codes
    private fun getWeatherCondition(code: Int): String {
        return when (code) {
            0 -> "Soleil"
            1, 2, 3 -> "Nuageux"
            45, 48 -> "Brouillard"
            51, 53, 55, 56, 57 -> "Bruine"
            61, 63, 65, 66, 67 -> "Pluie"
            71, 73, 75, 77 -> "Neige"
            80, 81, 82 -> "Averses"
            85, 86 -> "Averses de neige"
            95, 96, 99 -> "Orages"
            else -> "Inconnu"
        }
    }
}
