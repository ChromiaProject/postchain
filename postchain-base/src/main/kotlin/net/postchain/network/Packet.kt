// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network

import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.core.NodeRid

interface XPacketEncoder<PacketType> {
    fun makeIdentPacket(forNode: NodeRid): ByteArray
    fun makeVersionPacket(): ByteArray
    fun encodePacket(packet: PacketType, packetVersion: Long): ByteArray
}

interface XPacketEncoderFactory<PacketType> {
    fun create(config: PeerCommConfiguration, blockchainRID: BlockchainRid): XPacketEncoder<PacketType>
}

interface XPacketDecoder<PacketType> {
    fun parseIdentPacket(rawMessage: ByteArray): IdentPacketInfo
    fun parseVersionPacket(rawMessage: ByteArray): Long
    fun decodePacket(pubKey: ByteArray, rawMessage: ByteArray, packetVersion: Long): PacketType
    fun decodePacket(rawMessage: ByteArray, packetVersion: Long): PacketType?
    fun isIdentPacket(rawMessage: ByteArray): Boolean
    fun isVersionPacket(rawMessage: ByteArray): Boolean
}

interface XPacketDecoderFactory<PacketType> {
    fun create(config: PeerCommConfiguration): XPacketDecoder<PacketType>
}

data class IdentPacketInfo(
        val nodeId: NodeRid, // It is *target* peer for outgoing packets and *source* peer for incoming packets.
        val blockchainRid: BlockchainRid,
        val sessionKey: ByteArray? = null,
        val ephemeralPubKey: ByteArray? = null)
