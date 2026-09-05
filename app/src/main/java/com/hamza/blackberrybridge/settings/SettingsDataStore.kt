package com.hamza.blackberrybridge.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bridge_settings_prefs")

class SettingsDataStore(private val context: Context) {

    companion object {
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val NOTIFICATION_FORWARDING = booleanPreferencesKey("notification_forwarding")
        val MEDIA_CONTROL = booleanPreferencesKey("media_control")
        val ALLOW_CALLS = booleanPreferencesKey("allow_calls")
        val ALLOW_SMS = booleanPreferencesKey("allow_sms")

    }

    val autoConnectFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_CONNECT] ?: true
    }

    val notificationForwardingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATION_FORWARDING] ?: true
    }

    val mediaControlFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[MEDIA_CONTROL] ?: true
    }

    
    val allowCallsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ALLOW_CALLS] ?: true
    }
    val allowSmsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ALLOW_SMS] ?: true
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CONNECT] = enabled
        }
    }

    suspend fun setNotificationForwarding(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_FORWARDING] = enabled
        }
    }

    suspend fun setMediaControl(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MEDIA_CONTROL] = enabled
        }
    }
    suspend fun setAllowCalls(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ALLOW_CALLS] = enabled
        }
    }
    suspend fun setAllowSms(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ALLOW_SMS] = enabled
        }
    }

}
