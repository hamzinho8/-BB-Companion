with open('settings.gradle.kts', 'r') as f:
    content = f.read()

content = content.replace('rootProject.name = "BB Companion"', 'rootProject.name = "BB Compagnon"')

with open('settings.gradle.kts', 'w') as f:
    f.write(content)
