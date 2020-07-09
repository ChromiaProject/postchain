package net.postchain.network.masterslave

import net.postchain.network.x.LazyPacket

interface NodeConnection {
    fun accept(handler: PacketHandler)
    fun sendPacket(packet: LazyPacket)
    fun remoteAddress(): String
    fun close()
}