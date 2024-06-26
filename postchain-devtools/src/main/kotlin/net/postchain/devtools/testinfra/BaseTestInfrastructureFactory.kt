// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.base.BaseBlockchainProcessManager
import net.postchain.config.app.AppConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.config.node.ManualNodeConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.BlockchainRestartNotifier
import net.postchain.core.BlockchainState
import net.postchain.core.InfrastructureFactory
import net.postchain.core.Storage
import net.postchain.core.SynchronizationInfrastructure
import net.postchain.ebft.EbftPacketCodecFactory
import net.postchain.network.common.ConnectionManager
import net.postchain.network.peer.DefaultPeerConnectionManager

class TestBlockchainProcess(override val blockchainEngine: BlockchainEngine) : BlockchainProcess {

    companion object : KLogging()

    // Need this stuff to make this test class look a bit "normal"

    override fun start() {}

    override fun shutdown() {
        shutdownDebug("Begin")
        blockchainEngine.shutdown()
        shutdownDebug("End")
    }

    override fun isSigner(): Boolean = true

    override fun getBlockchainState(): BlockchainState = BlockchainState.RUNNING

    override fun isProcessRunning() = true

    private fun shutdownDebug(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug("shutdown() - $str.")
        }
    }
}


class TestSynchronizationInfrastructure : SynchronizationInfrastructure {

    override fun makeBlockchainProcess(
            engine: BlockchainEngine,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            restartNotifier: BlockchainRestartNotifier,
            blockchainState: BlockchainState
    ): BlockchainProcess {
        return TestBlockchainProcess(engine)
    }

    override fun exitBlockchainProcess(process: BlockchainProcess) = Unit
    override fun restartBlockchainProcess(process: BlockchainProcess) = Unit

    override fun shutdown() = Unit
}

class BaseTestInfrastructureFactory : InfrastructureFactory {

    override fun makeNodeConfigurationProvider(appConfig: AppConfig, storage: Storage): NodeConfigurationProvider {
        return ManualNodeConfigurationProvider(appConfig, storage)
    }

    override fun makeConnectionManager(appConfig: AppConfig): ConnectionManager =
            DefaultPeerConnectionManager(EbftPacketCodecFactory())


    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider =
            ManualBlockchainConfigurationProvider()


    override fun makeBlockchainInfrastructure(postchainContext: PostchainContext): BlockchainInfrastructure {
        with(postchainContext) {
            val syncInfra = TestSynchronizationInfrastructure()
            val restApiConfig = RestApiConfig.fromAppConfig(appConfig)
            val apiInfra = BaseApiInfrastructure(restApiConfig, nodeDiagnosticContext, postchainContext)

            return BaseBlockchainInfrastructure(syncInfra, apiInfra, this)
        }
    }

    override fun makeProcessManager(
            postchainContext: PostchainContext,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider
    ): BlockchainProcessManager {
        return BaseBlockchainProcessManager(postchainContext,
                blockchainInfrastructure,
                blockchainConfigurationProvider,
                listOf()
        )
    }
}
