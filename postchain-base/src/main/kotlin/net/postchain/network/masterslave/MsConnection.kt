package net.postchain.network.masterslave

import net.postchain.network.masterslave.protocol.MsMessage
import net.postchain.network.x.LazyPacket

interface MsMessageHandler {
    fun onMessage(message: MsMessage)
}


interface MsConnection {
    fun accept(handler: MsMessageHandler)
    fun sendPacket(packet: LazyPacket)
    fun remoteAddress(): String
    fun close()
}

