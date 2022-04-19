// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.InfrastructureFactory
import net.postchain.core.SynchronizationInfrastructure
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.EbftPacketDecoderFactory
import net.postchain.ebft.EbftPacketEncoderFactory
import net.postchain.network.common.ConnectionManager
import net.postchain.network.peer.DefaultPeerConnectionManager

class TestBlockchainProcess(override val blockchainEngine: BlockchainEngine) : BlockchainProcess {

    companion object : KLogging()

    // Need this stuff to make this test class look a bit "normal"
    val name: String = BlockchainProcessName("?", blockchainEngine.getConfiguration().blockchainRid).toString()

    override fun start() {}

    override fun shutdown() {
        shutdownDebug("Begin")
        blockchainEngine.shutdown()
        shutdownDebug("End")
    }

    private fun shutdownDebug(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug("$name: shutdown() - $str.")
        }
    }
}


class TestSynchronizationInfrastructure : SynchronizationInfrastructure {

    override fun makeBlockchainProcess(
            processName: BlockchainProcessName,
            engine: BlockchainEngine,
            shouldProcessNewMessages: (Long) -> Boolean
    ): BlockchainProcess {
        return TestBlockchainProcess(engine)
    }

    override fun exitBlockchainProcess(process: BlockchainProcess) = Unit
    override fun restartBlockchainProcess(process: BlockchainProcess) = Unit

    override fun shutdown() = Unit
}

class BaseTestInfrastructureFactory : InfrastructureFactory {

    override fun makeConnectionManager(nodeConfigProvider: NodeConfigurationProvider): ConnectionManager {
        return DefaultPeerConnectionManager(
                EbftPacketEncoderFactory(),
                EbftPacketDecoderFactory(),
                SECP256K1CryptoSystem()
        )
    }

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return ManualBlockchainConfigurationProvider()
    }

    override fun makeBlockchainInfrastructure(postchainContext: PostchainContext): BlockchainInfrastructure {
        with(postchainContext) {
            val syncInfra = TestSynchronizationInfrastructure()
            val apiInfra = BaseApiInfrastructure(nodeConfig, nodeDiagnosticContext)

            return BaseBlockchainInfrastructure(syncInfra, apiInfra, this)
        }
    }

    override fun makeProcessManager(
            postchainContext: PostchainContext,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider
    ): BlockchainProcessManager {
        return BaseBlockchainProcessManager(postchainContext, blockchainInfrastructure, blockchainConfigurationProvider)
    }
}
