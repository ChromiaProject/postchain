package net.postchain.ebft

import net.postchain.ebft.message.EbftMessage
import net.postchain.network.PacketConverter
import net.postchain.network.netty.NettyIO

class EbftPacketConverter : PacketConverter<EbftMessage> {

    override fun makeIdentPacket(forPeer: ByteArray) = forPeer

    override fun parseIdentPacket(bytes: ByteArray) = NettyIO.parseIdentPacket(bytes)

    override fun decodePacket(pubKey: ByteArray, bytes: ByteArray): EbftMessage {
        return EbftMessage.decode(bytes)
    }

    override fun encodePacket(packet: EbftMessage) = packet.encode()
}