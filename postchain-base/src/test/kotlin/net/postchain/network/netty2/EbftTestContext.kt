// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import net.postchain.common.BlockchainRid
import net.postchain.base.PeerCommConfiguration
import net.postchain.ebft.EbftPacketDecoder
import net.postchain.ebft.EbftPacketEncoder
import net.postchain.ebft.message.EbftMessage
import net.postchain.network.common.NodeConnectorEvents
import net.postchain.network.peer.PeerPacketHandler
import net.postchain.network.peer.PeerConnectionDescriptor

class EbftTestContext(val config: PeerCommConfiguration, val blockchainRid: BlockchainRid) {

    val packets: PeerPacketHandler = mock()

    val events: NodeConnectorEvents<PeerPacketHandler, PeerConnectionDescriptor> = mock {
        on { onNodeConnected(any()) } doReturn packets
    }

    val peer = NettyPeerConnector<EbftMessage>(events)

    fun init() = peer.init(config.myPeerInfo(), EbftPacketEncoder(config, blockchainRid), EbftPacketDecoder(config))

    fun buildPacketEncoder(): EbftPacketEncoder = EbftPacketEncoder(config, blockchainRid)

    fun buildPacketDecoder(): EbftPacketDecoder = EbftPacketDecoder(config)

    fun encodePacket(message: EbftMessage, version: Long): ByteArray = buildPacketEncoder().encodePacket(message, version)

    fun decodePacket(bytes: ByteArray, version: Long): EbftMessage = buildPacketDecoder().decodePacket(bytes, version)!!

    fun shutdown() = peer.shutdown()
}