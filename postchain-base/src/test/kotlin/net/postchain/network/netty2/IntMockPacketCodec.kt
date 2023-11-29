// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.core.NodeRid
import net.postchain.network.IdentPacketInfo
import net.postchain.network.XPacketCodec
import java.nio.ByteBuffer

const val INT_PACKET_VERSION = 42L
val INT_PACKET_VERSION_ARRAY = "$INT_PACKET_VERSION".toByteArray()

class IntMockPacketCodec(
        private val ownerPeerInfo: PeerInfo,
        private val peerInfos: Array<PeerInfo>
) : XPacketCodec<Int> {

    override fun getPacketVersion(): Long = INT_PACKET_VERSION

    // FYI: [et]: This logic corresponds to the [EbftPacketConverter]'s one (ignore [forPeer] here)
    override fun makeIdentPacket(forNode: NodeRid): ByteArray = ownerPeerInfo.pubKey

    override fun makeVersionPacket(): ByteArray = INT_PACKET_VERSION_ARRAY

    override fun encodePacket(packet: Int, packetVersion: Long): ByteArray = ByteBuffer.allocate(4).putInt(packet).array()

    override fun parseIdentPacket(rawMessage: ByteArray): IdentPacketInfo = IdentPacketInfo(NodeRid(rawMessage), BlockchainRid.ZERO_RID)

    override fun parseVersionPacket(rawMessage: ByteArray): Long = rawMessage.decodeToString().toLong()

    override fun getVersionFromVersionPacket(packet: Int): Long = packet.toLong()

    override fun decodePacket(pubKey: ByteArray, rawMessage: ByteArray, packetVersion: Long): Int = ByteBuffer.wrap(rawMessage).int

    override fun decodePacket(rawMessage: ByteArray, packetVersion: Long): Int? = ByteBuffer.wrap(rawMessage).int

    override fun isIdentPacket(rawMessage: ByteArray): Boolean = peerInfos.any { it.pubKey.contentEquals(rawMessage) }

    override fun isVersionPacket(rawMessage: ByteArray): Boolean = rawMessage.contentEquals(INT_PACKET_VERSION_ARRAY)

    override fun isVersionPacket(packet: Int): Boolean = packet.toLong() == INT_PACKET_VERSION
}