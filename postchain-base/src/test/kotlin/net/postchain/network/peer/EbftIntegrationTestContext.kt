// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.ebft.EbftPacketCodec
import net.postchain.ebft.EbftPacketCodecFactory
import net.postchain.ebft.message.ebftMessageToString
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.Closeable

class EbftIntegrationTestContext(
        config: PeerCommConfiguration,
        blockchainRid: BlockchainRid
) : Closeable {

    val chainId = 1L

    private val appConfig = AppConfig.fromEnvironment()

    private val nodeConfig: NodeConfig = mock {
        on { appConfig } doReturn appConfig
    }

    private val nodeConfigProvider: NodeConfigurationProvider = mock {
        on { getConfiguration() } doReturn nodeConfig
    }

    val connectionManager = DefaultPeerConnectionManager(nodeConfigProvider, EbftPacketCodecFactory())

    val communicationManager = DefaultPeerCommunicationManager(
            connectionManager,
            config,
            chainId,
            blockchainRid,
            EbftPacketCodec(config, blockchainRid),
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