// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.l2.Web3Connector
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

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
        val web3j = Web3j.build(HttpService("https://mainnet.infura.io/v3/6e8d7fef09c9485daac48699bea64f66"))
        val web3c = Web3Connector(web3j, "0x8a2279d4a90b6fe1c4b30fa660cc9f926797baa2")
        engine.setWeb3Connector(web3c)
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
