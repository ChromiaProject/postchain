// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import org.mockito.kotlin.mock
import net.postchain.core.BlockchainRid
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.ebft.EbftPacketDecoder
import net.postchain.ebft.EbftPacketDecoderFactory
import net.postchain.ebft.EbftPacketEncoder
import net.postchain.ebft.EbftPacketEncoderFactory
import net.postchain.ebft.message.EbftMessage
import java.io.Closeable

class EbftIntegrationTestContext(
        config: PeerCommConfiguration,
        blockchainRid: BlockchainRid
) : Closeable {

    val chainId = 1L
    //private val connectorFactory = NettyPeerConnectorFactory<Message>()

    val connectionManager = DefaultPeerConnectionManager<EbftMessage>(
            EbftPacketEncoderFactory(),
            EbftPacketDecoderFactory(),
            SECP256K1CryptoSystem())

    val communicationManager = DefaultPeerCommunicationManager(
            connectionManager,
            config,
            chainId,
            blockchainRid,
            EbftPacketEncoder(config, blockchainRid),
            EbftPacketDecoder(config),
            mock())

    fun shutdown() {
        communicationManager.shutdown()
        connectionManager.shutdown()
    }

    override fun close() {
        shutdown()
    }
}