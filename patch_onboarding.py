import re

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'r') as f:
    content = f.read()

replacements = {
    '"Bridge to the Past"': '"Un Pont vers le Passé"',
    '"Transform your BlackBerry Bold 9790 into a modern companion terminal."': '"Transformez votre BlackBerry Bold 9790 en un terminal compagnon moderne."',
    '"Smart Sync"': '"Synchronisation Intelligente"',
    '"Seamlessly sync notifications, calls, clipboard, and music via Bluetooth Classic."': '"Synchronisez facilement les notifications, les appels, le presse-papiers et la musique via Bluetooth Classic."',
    '"Privacy First"': '"La Confidentialité d\'abord"',
    '"All data remains local between your phone and BlackBerry. We respect your digital boundaries."': '"Toutes les données restent locales entre votre téléphone et votre BlackBerry. Nous respectons votre vie privée."',
    '"Start Bridge"': '"Démarrer"',
    '"Next"': '"Suivant"',
    '"Scan for BlackBerry Devices"': '"Rechercher des appareils BlackBerry"',
    '"BBOS Companion"': '"Compagnon BBOS"',
}

for k, v in replacements.items():
    content = content.replace(k, v)

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'w') as f:
    f.write(content)

