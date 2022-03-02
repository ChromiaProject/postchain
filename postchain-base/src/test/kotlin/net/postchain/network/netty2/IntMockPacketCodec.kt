// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import net.postchain.core.BlockchainRid
import net.postchain.core.NodeRid
import net.postchain.base.PeerInfo
import net.postchain.core.ByteArrayKey
import net.postchain.network.IdentPacketInfo
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketEncoder
import java.nio.ByteBuffer

class IntMockPacketEncoder(
        private val ownerPeerInfo: PeerInfo
) : XPacketEncoder<Int> {

    // FYI: [et]: This logic corresponds to the [EbftPacketConverter]'s one (ignore [forPeer] here)
    override fun makeIdentPacket(forNode: NodeRid): ByteArray = ownerPeerInfo.pubKey

    override fun encodePacket(packet: Int): ByteArray = ByteBuffer.allocate(4).putInt(packet).array()
}

class IntMockPacketDecoder(
        private val peerInfos: Array<PeerInfo>
) : XPacketDecoder<Int> {

    // FYI: [et]: This logic corresponds to the [EbftPacketConverter]'s one
    override fun parseIdentPacket(bytes: ByteArray): IdentPacketInfo = IdentPacketInfo(NodeRid(bytes), BlockchainRid.ZERO_RID)

    override fun decodePacket(pubKey: ByteArray, bytes: ByteArray): Int = ByteBuffer.wrap(bytes).int

    override fun decodePacket(bytes: ByteArray): Int? = ByteBuffer.wrap(bytes).int

    override fun isIdentPacket(bytes: ByteArray): Boolean = peerInfos.any { it.pubKey.contentEquals(bytes) }

}