package com.hamza.blackberrybridge.protocol

import org.junit.Assert.*
import org.junit.Test

class CommandParserTest {

    @Test
    fun `test PING command parsing`() {
        val packet = CommandParser.parse("PING\n")
        assertNotNull(packet)
        assertEquals("PING", packet?.command)
        assertTrue(packet?.args?.isEmpty() == true)
    }

    @Test
    fun `test HELLO command parsing with args`() {
        val packet = CommandParser.parse("HELLO|BSB/1|ANDROID_DEVICE\n")
        assertNotNull(packet)
        assertEquals("HELLO", packet?.command)
        assertEquals(2, packet?.args?.size)
        assertEquals("BSB/1", packet?.args?.get(0))
        assertEquals("ANDROID_DEVICE", packet?.args?.get(1))
    }

    @Test
    fun `test BSBPacket formatting`() {
        val packet = BSBPacket("NOTIFICATION", listOf("1", "WhatsApp", "Mohamed", "Salut"))
        assertEquals("NOTIFICATION|1|WhatsApp|Mohamed|Salut\n", packet.toString())
    }

    @Test
    fun `test allowed commands check`() {
        assertTrue(CommandParser.isCommandAllowed(BSBPacket("HELLO", emptyList())))
        assertFalse(CommandParser.isCommandAllowed(BSBPacket("HACK", emptyList())))
    }
}
