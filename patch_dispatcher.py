with open('app/src/main/java/com/hamza/blackberrybridge/protocol/CommandDispatcher.kt', 'r') as f:
    content = f.read()

replacements = {
    'CallController.answerCall(service, packet.args[0])': 'CallController.answerCall(service, packet.args[0])\n                    BridgeStateManager.logEvent("Appel répondu", com.hamza.blackberrybridge.state.EventType.SUCCESS)',
    'CallController.rejectCall(service, packet.args[0])': 'CallController.rejectCall(service, packet.args[0])\n                    BridgeStateManager.logEvent("Appel rejeté", com.hamza.blackberrybridge.state.EventType.WARNING)',
    'MediaSessionController.dispatchMediaCommand(service, packet.command)': 'MediaSessionController.dispatchMediaCommand(service, packet.command)\n                BridgeStateManager.logEvent("Contrôle Média: ${packet.command}", com.hamza.blackberrybridge.state.EventType.INFO)'
}

for k, v in replacements.items():
    content = content.replace(k, v)

with open('app/src/main/java/com/hamza/blackberrybridge/protocol/CommandDispatcher.kt', 'w') as f:
    f.write(content)
