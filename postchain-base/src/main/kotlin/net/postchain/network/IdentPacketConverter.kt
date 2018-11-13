package net.postchain.network

import net.postchain.base.PeerID
import net.postchain.network.x.XPeerConnectionDescriptor

interface IdentPacketConverter {
    fun makeIdentPacket(connectionDescriptor: XPeerConnectionDescriptor, ephemeralPubKey: ByteArray, pubKey: ByteArray): ByteArray
    fun parseIdentPacket(bytes: ByteArray): IdentPacketInfo
}