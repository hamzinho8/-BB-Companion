with open('app/src/main/java/com/hamza/blackberrybridge/notification/BridgeNotificationListener.kt', 'r') as f:
    content = f.read()

new_logic = """
        val packageName = sbn.packageName
        if (packageName == applicationContext.packageName) return
        
        val dataStore = com.hamza.blackberrybridge.settings.SettingsDataStore(applicationContext)
        val allowNotif = kotlinx.coroutines.runBlocking { dataStore.notificationForwardingFlow.kotlinx.coroutines.flow.first() }
        // using proper import:
        
"""

# Let's write the whole file carefully to avoid import issues.
import_add = """import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
"""
if "import kotlinx.coroutines.runBlocking" not in content:
    content = content.replace("import android.util.Log", "import android.util.Log\n" + import_add)

replace_target = """        val packageName = sbn.packageName
        if (packageName == applicationContext.packageName) return"""
        
replacement = """        val packageName = sbn.packageName
        if (packageName == applicationContext.packageName) return
        
        val dataStore = com.hamza.blackberrybridge.settings.SettingsDataStore(applicationContext)
        val allowNotif = runBlocking { dataStore.notificationForwardingFlow.first() }
        if (!allowNotif) return
"""

content = content.replace(replace_target, replacement)

with open('app/src/main/java/com/hamza/blackberrybridge/notification/BridgeNotificationListener.kt', 'w') as f:
    f.write(content)
