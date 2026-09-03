package com.hamza.blackberrybridge.protocol

data class BSBPacket(
    val command: String,
    val args: List<String>
) {
    override fun toString(): String {
        return if (args.isEmpty()) {
            "$command\n"
        } else {
            "$command|${args.joinToString("|")}\n"
        }
    }
}
