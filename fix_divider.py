with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'r') as f:
    content = f.read()

content = content.replace("Divider(color = BorderDark, modifier = Modifier.padding(vertical = 8.dp))", "HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 8.dp))")

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'w') as f:
    f.write(content)
