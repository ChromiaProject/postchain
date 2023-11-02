// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network

import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.core.NodeRid

interface XPacketCodec<PacketType> {
    fun getPacketVersion(): Long

    fun makeIdentPacket(forNode: NodeRid): ByteArray
    fun makeVersionPacket(): ByteArray
    fun encodePacket(packet: PacketType, packetVersion: Long): ByteArray

    fun parseIdentPacket(rawMessage: ByteArray): IdentPacketInfo
    fun parseVersionPacket(rawMessage: ByteArray): Long
    fun decodePacket(pubKey: ByteArray, rawMessage: ByteArray, packetVersion: Long): PacketType
    fun decodePacket(rawMessage: ByteArray, packetVersion: Long): PacketType?
    fun isIdentPacket(rawMessage: ByteArray): Boolean
    fun isVersionPacket(rawMessage: ByteArray): Boolean
}

interface XPacketCodecFactory<PacketType> {
    fun create(config: PeerCommConfiguration, blockchainRID: BlockchainRid): XPacketCodec<PacketType>
}

data class IdentPacketInfo(
        val nodeId: NodeRid, // It is *target* peer for outgoing packets and *source* peer for incoming packets.
        val blockchainRid: BlockchainRid,
        val sessionKey: ByteArray? = null,
        val ephemeralPubKey: ByteArray? = null)
