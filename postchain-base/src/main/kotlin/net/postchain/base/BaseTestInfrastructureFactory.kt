// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.heartbeat.HeartbeatChecker
import net.postchain.ebft.heartbeat.HeartbeatEvent

class TestBlockchainProcess(val _engine: BlockchainEngine) : BlockchainProcess {
    override fun getEngine(): BlockchainEngine {
        return _engine
    }

    override fun shutdown() {
        _engine.shutdown()
    }

    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) {
    }
}


class TestSynchronizationInfrastructure : SynchronizationInfrastructure {

    override fun init() = Unit

    override fun makeBlockchainProcess(processName: BlockchainProcessName, engine: BlockchainEngine, heartbeatChecker: HeartbeatChecker, historicBlockchain: HistoricBlockchain?): BlockchainProcess {
        return TestBlockchainProcess(engine)
    }

    override fun makeHeartbeatChecker(chainId: Long): HeartbeatChecker {
        return object : HeartbeatChecker {
            override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) = Unit
            override fun checkHeartbeat(timestamp: Long): Boolean = true
        }
    }

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
