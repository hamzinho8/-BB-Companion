with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'r') as f:
    content = f.read()

imports = """
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
"""

if "import androidx.compose.material3.FloatingActionButton" not in content:
    content = content.replace("import androidx.compose.material3.SwitchDefaults", "import androidx.compose.material3.SwitchDefaults\n" + imports)
    with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'w') as f:
        f.write(content)
    print("Imports added")
else:
    print("Already there")
