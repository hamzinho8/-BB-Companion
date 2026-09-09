import os

# 1. Update AndroidManifest.xml
with open('app/src/main/AndroidManifest.xml', 'r') as f:
    content = f.read()
if 'android.permission.CALL_PHONE' not in content:
    content = content.replace(
        '<uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />',
        '<uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />\n    <uses-permission android:name="android.permission.CALL_PHONE" />'
    )
with open('app/src/main/AndroidManifest.xml', 'w') as f:
    f.write(content)

# 2. Update MainActivity.kt
with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'r') as f:
    content = f.read()
if 'Manifest.permission.CALL_PHONE' not in content:
    content = content.replace(
        'permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)',
        'permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)\n        permissions.add(Manifest.permission.CALL_PHONE)'
    )
with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'w') as f:
    f.write(content)

# 3. Update CallController.kt
with open('app/src/main/java/com/hamza/blackberrybridge/calls/CallController.kt', 'r') as f:
    content = f.read()
if 'fun makeCall(' not in content:
    make_call_func = """    @SuppressLint("MissingPermission")
    fun makeCall(context: Context, phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_CALL)
                intent.data = android.net.Uri.parse("tel:$phoneNumber")
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Initiated outbound call to $phoneNumber")
                
                // Optionnel : Activer le haut-parleur pour le kit mains-libres
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                        audioManager.mode = android.media.AudioManager.MODE_IN_CALL
                        audioManager.isSpeakerphoneOn = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to enable speakerphone for outbound call", e)
                    }
                }, 2000)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to make call", e)
                sendError(context, phoneNumber, "FAILED", "EXCEPTION")
            }
        } else {
            sendError(context, phoneNumber, "FAILED", "RESTRICTED_PERMISSION_MISSING")
        }
    }

    private fun sendError"""
    content = content.replace('    private fun sendError', make_call_func)
with open('app/src/main/java/com/hamza/blackberrybridge/calls/CallController.kt', 'w') as f:
    f.write(content)

# 4. Update CommandDispatcher.kt
with open('app/src/main/java/com/hamza/blackberrybridge/protocol/CommandDispatcher.kt', 'r') as f:
    content = f.read()
if '"CALL_OUTBOUND"' not in content:
    call_dispatch = """            "CALL_REJECT" -> {
                if (packet.args.isNotEmpty()) CallController.rejectCall(service, packet.args[0])
                    BridgeStateManager.logEvent("Appel rejeté", com.hamza.blackberrybridge.state.EventType.WARNING)
            }
            "CALL_OUTBOUND" -> {
                if (packet.args.isNotEmpty()) {
                    CallController.makeCall(service, packet.args[0])
                    BridgeStateManager.logEvent("Appel sortant: ${packet.args[0]}", com.hamza.blackberrybridge.state.EventType.SUCCESS)
                }
            }"""
    content = content.replace("""            "CALL_REJECT" -> {
                if (packet.args.isNotEmpty()) CallController.rejectCall(service, packet.args[0])
                    BridgeStateManager.logEvent("Appel rejeté", com.hamza.blackberrybridge.state.EventType.WARNING)
            }""", call_dispatch)
with open('app/src/main/java/com/hamza/blackberrybridge/protocol/CommandDispatcher.kt', 'w') as f:
    f.write(content)

