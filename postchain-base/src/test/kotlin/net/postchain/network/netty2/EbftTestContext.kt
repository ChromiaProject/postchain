// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.ebft.EbftPacketCodec
import net.postchain.ebft.message.EbftMessage
import net.postchain.network.common.NodeConnectorEvents
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.peer.PeerPacketHandler
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class EbftTestContext(val config: PeerCommConfiguration, val blockchainRid: BlockchainRid) {

    val packets: PeerPacketHandler = mock()

    val events: NodeConnectorEvents<PeerPacketHandler, PeerConnectionDescriptor> = mock {
        on { onNodeConnected(any()) } doReturn packets
    }

    val peer = NettyPeerConnector<EbftMessage>(events)

    fun init() = peer.init(config.myPeerInfo(), EbftPacketCodec(config, blockchainRid))

    fun buildPacketCodec(): EbftPacketCodec = EbftPacketCodec(config, blockchainRid)

    fun encodePacket(message: EbftMessage, version: Long): ByteArray = buildPacketCodec().encodePacket(message, version)

    fun decodePacket(bytes: ByteArray, version: Long): EbftMessage = buildPacketCodec().decodePacket(bytes, version)!!

    fun shutdown() = peer.shutdown()
}