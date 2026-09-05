with open('app/src/main/java/com/hamza/blackberrybridge/calls/CallStateReceiver.kt', 'r') as f:
    content = f.read()

import_add = """import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.hamza.blackberrybridge.settings.SettingsDataStore
"""
if "import kotlinx.coroutines.runBlocking" not in content:
    content = content.replace("import android.telephony.TelephonyManager", "import android.telephony.TelephonyManager\n" + import_add)

replace_target = """        if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {"""
replacement = """        if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            context?.let { ctx ->
                val dataStore = SettingsDataStore(ctx)
                val allowCalls = runBlocking { dataStore.allowCallsFlow.first() }
                if (!allowCalls) return
            }
"""
content = content.replace(replace_target, replacement)

with open('app/src/main/java/com/hamza/blackberrybridge/calls/CallStateReceiver.kt', 'w') as f:
    f.write(content)
