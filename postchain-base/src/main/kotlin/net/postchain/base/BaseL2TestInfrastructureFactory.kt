// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.base.l2.L2BlockchainConfiguration
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.l2.L2SpecialTxHandler
import net.postchain.l2.L2TestEventProcessor

class TestL2BlockchainProcess(private val _engine: BlockchainEngine) : BlockchainProcess {
    override fun getEngine(): BlockchainEngine {
        return _engine
    }

    override fun shutdown() {
        _engine.shutdown()
    }
}


class TestL2SynchronizationInfrastructure : SynchronizationInfrastructure {

    override fun makeBlockchainProcess(
        processName: BlockchainProcessName,
        engine: BlockchainEngine,
        historicBlockchainContext: HistoricBlockchainContext?
    ): BlockchainProcess {
        val proc = L2TestEventProcessor(engine.getBlockQueries())
        ((engine.getConfiguration() as L2BlockchainConfiguration).getSpecialTxHandler() as L2SpecialTxHandler).useEventProcessor(proc)
        return TestL2BlockchainProcess(engine)
    }

    override fun shutdown() {}
}

class BaseL2TestInfrastructureFactory : InfrastructureFactory {

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return ManualBlockchainConfigurationProvider()
    }

    override fun makeBlockchainInfrastructure(
        nodeConfigProvider: NodeConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainInfrastructure {

        val syncInfra = TestL2SynchronizationInfrastructure()
        val apiInfra = BaseApiInfrastructure(nodeConfigProvider, nodeDiagnosticContext)

        return BaseBlockchainInfrastructure(
            nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext
        )
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
            nodeDiagnosticContext
        )
    }
}
