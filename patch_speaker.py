with open('app/src/main/java/com/hamza/blackberrybridge/calls/CallController.kt', 'r') as f:
    content = f.read()

old_answer = """                    telecomManager.acceptRingingCall()
                    Log.d(TAG, "Call answered successfully")"""

new_answer = """                    telecomManager.acceptRingingCall()
                    Log.d(TAG, "Call answered successfully")
                    
                    // Activer le haut-parleur automatiquement
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                            audioManager.mode = android.media.AudioManager.MODE_IN_CALL
                            audioManager.isSpeakerphoneOn = true
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to enable speakerphone", e)
                        }
                    }, 1500) // Délai pour laisser le temps à l'appel de s'établir"""

content = content.replace(old_answer, new_answer)

with open('app/src/main/java/com/hamza/blackberrybridge/calls/CallController.kt', 'w') as f:
    f.write(content)
