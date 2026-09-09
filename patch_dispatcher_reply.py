with open('app/src/main/java/com/hamza/blackberrybridge/protocol/CommandDispatcher.kt', 'r') as f:
    content = f.read()

old_voice = """            "VOICE_REPLY" -> {
                if (packet.args.size >= 2) {
                    val notifId = packet.args[0]
                    val base64 = packet.args[1]
                    VoiceReplyManager.handleVoiceReply(service, notifId, base64)
                }
            }"""

new_voice = """            "REPLY_MSG" -> {
                if (packet.args.size >= 2) {
                    val notifId = packet.args[0]
                    val messageText = packet.args[1]
                    com.hamza.blackberrybridge.notification.BridgeNotificationListener.instance?.replyToMessage(notifId, messageText)
                }
            }
            "VOICE_REPLY" -> {
                if (packet.args.size >= 2) {
                    val notifId = packet.args[0]
                    val base64 = packet.args[1]
                    VoiceReplyManager.handleVoiceReply(service, notifId, base64)
                }
            }"""
        
content = content.replace(old_voice, new_voice)

with open('app/src/main/java/com/hamza/blackberrybridge/protocol/CommandDispatcher.kt', 'w') as f:
    f.write(content)
