package com.hamza.blackberrybridge.settings

import android.content.Context
import android.content.SharedPreferences

class BridgeSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bridge_settings", Context.MODE_PRIVATE)

    var isNotificationsEnabled: Boolean
        get() = prefs.getBoolean("enable_notifications", true)
        set(value) = prefs.edit().putBoolean("enable_notifications", value).apply()

    var isCallsEnabled: Boolean
        get() = prefs.getBoolean("enable_calls", true)
        set(value) = prefs.edit().putBoolean("enable_calls", value).apply()

    var isMediaEnabled: Boolean
        get() = prefs.getBoolean("enable_media", true)
        set(value) = prefs.edit().putBoolean("enable_media", value).apply()

    var isClipboardEnabled: Boolean
        get() = prefs.getBoolean("enable_clipboard", true)
        set(value) = prefs.edit().putBoolean("enable_clipboard", value).apply()

    var isWeatherEnabled: Boolean
        get() = prefs.getBoolean("enable_weather", true)
        set(value) = prefs.edit().putBoolean("enable_weather", value).apply()

    var isVoiceReplyEnabled: Boolean
        get() = prefs.getBoolean("enable_voice_reply", true)
        set(value) = prefs.edit().putBoolean("enable_voice_reply", value).apply()
}
