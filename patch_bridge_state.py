with open('app/src/main/java/com/hamza/blackberrybridge/state/BridgeStateManager.kt', 'r') as f:
    content = f.read()

content = content.replace('if (current.size > 5) {', 'if (current.size > 100) {')

with open('app/src/main/java/com/hamza/blackberrybridge/state/BridgeStateManager.kt', 'w') as f:
    f.write(content)
