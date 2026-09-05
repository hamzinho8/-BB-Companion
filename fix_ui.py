import re

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'r') as f:
    content = f.read()

# 1. Add `var showControlPanel by remember { mutableStateOf(false) }` to StatusContent
status_sig = "fun StatusContent(context: android.content.Context) {"
if "var showControlPanel by remember" not in content:
    replacement = status_sig + "\n    var showControlPanel by remember { mutableStateOf(false) }"
    content = content.replace(status_sig, replacement)

# 2. Add clickable to the Row inside StatusContent.
target_row = """                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    )"""

replacement_row = """                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable(enabled = isConnected) { showControlPanel = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    )"""

content = content.replace(target_row, replacement_row)

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'w') as f:
    f.write(content)
