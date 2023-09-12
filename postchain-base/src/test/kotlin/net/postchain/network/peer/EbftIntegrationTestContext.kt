// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.ebft.EbftPacketDecoder
import net.postchain.ebft.EbftPacketDecoderFactory
import net.postchain.ebft.EbftPacketEncoder
import net.postchain.ebft.EbftPacketEncoderFactory
import net.postchain.ebft.message.ebftMessageToString
import org.mockito.kotlin.mock
import java.io.Closeable

class EbftIntegrationTestContext(
        config: PeerCommConfiguration,
        blockchainRid: BlockchainRid
) : Closeable {

    val chainId = 1L
    //private val connectorFactory = NettyPeerConnectorFactory<Message>()

    val connectionManager = DefaultPeerConnectionManager(
            EbftPacketEncoderFactory(),
            EbftPacketDecoderFactory())

    val communicationManager = DefaultPeerCommunicationManager(
            connectionManager,
            config,
            chainId,
            blockchainRid,
            EbftPacketEncoder(config, blockchainRid),
            EbftPacketDecoder(config),
            ebftMessageToString(mock())
    )

    fun shutdown() {
        communicationManager.shutdown()
        connectionManager.shutdown()
    }

    override fun close() {
        shutdown()
    }
}