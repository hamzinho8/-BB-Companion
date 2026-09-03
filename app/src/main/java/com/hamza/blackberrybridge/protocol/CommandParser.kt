package com.hamza.blackberrybridge.protocol

object CommandParser {
    fun parse(line: String): BSBPacket? {
        val cleanLine = line.trimEnd('\n', '\r')
        if (cleanLine.isBlank()) return null
        
        val parts = cleanLine.split("|")
        val command = parts[0]
        val args = if (parts.size > 1) parts.drop(1) else emptyList()
        
        return BSBPacket(command, args)
    }

    fun isCommandAllowed(packet: BSBPacket): Boolean {
        // Whitelist of allowed incoming commands from BlackBerry
        val allowedCommands = setOf(
            "PING", "PONG", "HELLO", "READY", 
            "CALL_ANSWER", "CALL_REJECT",
            "MEDIA_PLAY", "MEDIA_PAUSE", "MEDIA_NEXT", "MEDIA_PREVIOUS",
            "FIND_PHONE", "FIND_PHONE_STOP",
            "CLIPBOARD", "OPEN_APP", "APP_LIST_REQUEST",
            "REPLY", "VOICE_REPLY", "NOTIFICATION_ACTION",
            "CONTACTS_REQUEST", "CONTACT_SEARCH",
            "SYNC_REQUEST"
        )
        return allowedCommands.contains(packet.command)
    }
}
