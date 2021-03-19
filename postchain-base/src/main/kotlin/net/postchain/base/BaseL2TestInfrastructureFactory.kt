// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.base.l2.L2BlockchainConfiguration
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.l2.EthereumEventProcessor
import net.postchain.l2.L2SpecialTxHandler

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
        engine: BlockchainEngine
    ): BlockchainProcess {
        val blockchainConfig = engine.getConfiguration() as L2BlockchainConfiguration // TODO: [et]: Resolve type cast
        val layer2 = blockchainConfig.configData.getLayer2()
        val url = layer2?.get("eth_rpc_api_node_url")?.asString() ?: "http://localhost:8545"
        val contractAddress = layer2?.get("contract_address")?.asString() ?: "0x0"
        val proc = EthereumEventProcessor(url, contractAddress, engine.getBlockQueries())
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
