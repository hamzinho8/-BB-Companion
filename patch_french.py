import re

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'r') as f:
    content = f.read()

replacements = {
    # MainScreen
    '"Home"': '"Accueil"',
    '"Status"': '"Statut"',
    '"Logs"': '"Journaux"',
    '"Settings"': '"Paramètres"',

    # StatusContent
    '"Bridge Service"': '"Service Bridge"',
    '"Active in background"': '"Actif en arrière-plan"',
    '"Stopped"': '"Arrêté"',
    '"Connected Device"': '"Appareil connecté"',
    '"Disconnected"': '"Déconnecté"',
    '"No active link"': '"Aucune connexion"',
    '"SPP/CONNECTED"': '"SPP/CONNECTÉ"',
    '"DISCONNECTED"': '"DÉCONNECTÉ"',
    '"Battery"': '"Batterie"',
    '"Sync"': '"Sync."',
    '"Active"': '"Actif"',
    '"Quick Actions"': '"Actions Rapides"',
    '"Find"': '"Sonner"',
    '"Lock"': '"Verrouiller"',
    '"Camera"': '"Caméra"',
    '"Media"': '"Médias"',
    '"Available Devices:"': '"Appareils disponibles :"',
    '"Recent Activity"': '"Activité Récente"',
    '"No recent activity."': '"Aucune activité récente."',

    # LogsContent
    '"System Logs"': '"Journaux Système"',
    '"[SYSTEM] Logging initialized..."': '"[SYSTÈME] Journaux initialisés..."',
    '"[SYSTEM] Connecting Bluetooth SPP..."': '"[SYSTÈME] Connexion Bluetooth SPP..."',

    # SettingsContent
    '"Health & Permissions"': '"Santé et Autorisations"',
    '"Notification Access"': '"Accès aux notifications"',
    '"Bluetooth Connect"': '"Connexion Bluetooth"',
    '"Contacts Access"': '"Accès aux contacts"',
    '"Configuration"': '"Configuration"',
    '"Auto-connect"': '"Connexion automatique"',
    '"Automatically connect to BB"': '"Se connecter automatiquement au BB"',
    '"Notification forwarding"': '"Transfert de notifications"',
    '"Forward incoming messages"': '"Transférer les messages entrants"',
    '"Media control"': '"Contrôle multimédia"',
    '"Control Spotify/Music"': '"Contrôler Spotify/Musique"',
    
    # Bottom Sheet
    '"Master Control Panel"': '"Panneau de Contrôle"',
    '"Synchronization Channels"': '"Canaux de Synchronisation"',
    '"Phone Calls"': '"Appels Téléphoniques"',
    '"Allow answering/rejecting calls"': '"Autoriser la réponse/le rejet"',
    '"Messages (SMS)"': '"Messages (SMS)"',
    '"Forward incoming text messages"': '"Transférer les SMS entrants"',
    '"Notifications"': '"Notifications"',
    '"Forward app notifications"': '"Transférer les alertes"',
    '"Alarms & Media"': '"Alarmes & Médias"',
    '"Sync alarms and playback controls"': '"Synchroniser alarmes et musique"',

    # Misc
    '"GRANTED"': '"AUTORISÉ"',
    '"DENIED"': '"REFUSÉ"',
}

for k, v in replacements.items():
    content = content.replace(k, v)

with open('app/src/main/java/com/hamza/blackberrybridge/ui/MainActivity.kt', 'w') as f:
    f.write(content)

