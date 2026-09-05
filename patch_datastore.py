with open('app/src/main/java/com/hamza/blackberrybridge/settings/SettingsDataStore.kt', 'r') as f:
    content = f.read()

new_keys = """
        val ALLOW_CALLS = booleanPreferencesKey("allow_calls")
        val ALLOW_SMS = booleanPreferencesKey("allow_sms")
"""
content = content.replace('val MEDIA_CONTROL = booleanPreferencesKey("media_control")', 'val MEDIA_CONTROL = booleanPreferencesKey("media_control")' + new_keys)

new_flows = """
    val allowCallsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ALLOW_CALLS] ?: true
    }
    val allowSmsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ALLOW_SMS] ?: true
    }
"""
content = content.replace('suspend fun setAutoConnect', new_flows + '\n    suspend fun setAutoConnect')

new_setters = """
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
"""
content = content.replace('suspend fun setMediaControl(enabled: Boolean) {\n        context.dataStore.edit { preferences ->\n            preferences[MEDIA_CONTROL] = enabled\n        }\n    }', 'suspend fun setMediaControl(enabled: Boolean) {\n        context.dataStore.edit { preferences ->\n            preferences[MEDIA_CONTROL] = enabled\n        }\n    }' + new_setters)

with open('app/src/main/java/com/hamza/blackberrybridge/settings/SettingsDataStore.kt', 'w') as f:
    f.write(content)
