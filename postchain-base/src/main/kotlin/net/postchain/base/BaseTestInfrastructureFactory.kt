// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.heartbeat.HeartbeatChecker
import net.postchain.ebft.heartbeat.HeartbeatEvent

class TestBlockchainProcess(override val blockchainEngine: BlockchainEngine) : BlockchainProcess {

    companion object : KLogging()

    // Need this stuff to make this test class look a bit "normal"
    val name: String = BlockchainProcessName("?", blockchainEngine.getConfiguration().blockchainRid).toString()

    override fun start() { }

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

    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) = Unit
}


class TestSynchronizationInfrastructure : SynchronizationInfrastructure {

    override fun init() = Unit

    override fun makeBlockchainProcess(
            processName: BlockchainProcessName,
            engine: BlockchainEngine,
            heartbeatChecker: HeartbeatChecker,
            historicBlockchainContext: HistoricBlockchainContext?
    ): BlockchainProcess {
        return TestBlockchainProcess(engine)
    }

    override fun makeHeartbeatChecker(chainId: Long, blockchainRid: BlockchainRid): HeartbeatChecker {
        return object : HeartbeatChecker {
            override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) = Unit
            override fun checkHeartbeat(timestamp: Long): Boolean = true
        }
    }

    override fun exitBlockchainProcess(process: BlockchainProcess) = Unit
    override fun restartBlockchainProcess(process: BlockchainProcess) = Unit

    override fun shutdown() = Unit
}

class BaseTestInfrastructureFactory : InfrastructureFactory {

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return ManualBlockchainConfigurationProvider()
    }

    override fun makeBlockchainInfrastructure(
            nodeConfigProvider: NodeConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainInfrastructure {

        val syncInfra = TestSynchronizationInfrastructure()
        val apiInfra = BaseApiInfrastructure(nodeConfigProvider, nodeDiagnosticContext)

        return BaseBlockchainInfrastructure(
                nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext)
    }

    override fun makeProcessManager(
            nodeConfigProvider: NodeConfigurationProvider,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainProcessManager {

        return BaseBlockchainProcessManager(
                blockchainInfrastructure,
                nodeConfigProvider,
                blockchainConfigurationProvider,
                nodeDiagnosticContext)
    }
}
