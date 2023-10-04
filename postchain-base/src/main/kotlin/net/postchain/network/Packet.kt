// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network

import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.core.NodeRid

interface XPacketEncoder<PacketType> {
    fun makeIdentPacket(forNode: NodeRid): ByteArray
    fun encodePacket(packet: PacketType): ByteArray
}

interface XPacketEncoderFactory<PacketType> {
    fun create(config: PeerCommConfiguration, blockchainRID: BlockchainRid): XPacketEncoder<PacketType>
}

interface XPacketDecoder<PacketType> {
    fun parseIdentPacket(rawMessage: ByteArray): IdentPacketInfo
    fun decodePacket(pubKey: ByteArray, rawMessage: ByteArray): PacketType
    fun decodePacket(rawMessage: ByteArray): PacketType?
    fun isIdentPacket(rawMessage: ByteArray): Boolean
}

interface XPacketDecoderFactory<PacketType> {
    fun create(config: PeerCommConfiguration): XPacketDecoder<PacketType>
}

data class IdentPacketInfo(
        val nodeId: NodeRid, // It is *target* peer for outgoing packets and *source* peer for incoming packets.
        val blockchainRid: BlockchainRid,
        val sessionKey: ByteArray? = null,
        val ephemeralPubKey: ByteArray? = null)
