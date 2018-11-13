package net.postchain.ebft

import net.postchain.ebft.message.EbftMessage
import net.postchain.network.IdentPacketInfo
import net.postchain.network.PacketConverter
import net.postchain.network.x.XPeerConnectionDescriptor

class EbftPacketConverter : PacketConverter<EbftMessage> {

    val identPacketDelimiter = "\\n".toByteArray()

    override fun makeIdentPacket(connectionDescriptor: XPeerConnectionDescriptor, ephemeralPubKey: ByteArray, pubKey: ByteArray) =
            createIdentPacketBytes(connectionDescriptor, ephemeralPubKey, pubKey)

    override fun parseIdentPacket(bytes: ByteArray): IdentPacketInfo {
        var lastStart = 0
        val result = mutableListOf<ByteArray>()
        bytes.forEachIndexed { indx, it ->
            if (indx != 0) {
                if (bytes[indx - 1] == identPacketDelimiter[0] && it == identPacketDelimiter[1]) {
                    if (indx - 2 >= 0)
                        result.add(bytes.sliceArray(lastStart..indx - 2))
                    lastStart = indx + 1
                }
            }
        }
        if (lastStart < bytes.size - 1) {
            result.add(bytes.sliceArray(lastStart..bytes.size - 1))
        }
        return IdentPacketInfo(result[0], result[1], ephemeralRemoteNodePubKey = result[2], remoteNodePubKey = result[3])
    }

    override fun decodePacket(pubKey: ByteArray, bytes: ByteArray): EbftMessage {
        return EbftMessage.decode(bytes)
    }

    override fun encodePacket(packet: EbftMessage) = packet.encode()

    fun createIdentPacketBytes(descriptor: XPeerConnectionDescriptor, ephemeralPubKey: ByteArray, pubKey: ByteArray) =
            descriptor.peerID.byteArray +
                    identPacketDelimiter +
                    descriptor.blockchainRID.byteArray +
                    identPacketDelimiter +
                    ephemeralPubKey +
                    identPacketDelimiter +
                    pubKey
}